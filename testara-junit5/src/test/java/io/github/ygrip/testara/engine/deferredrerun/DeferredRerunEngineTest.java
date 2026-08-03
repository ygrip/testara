package io.github.ygrip.testara.engine.deferredrerun;

import io.github.ygrip.testara.engine.deferredrerun.recover.DeferredRerunRecoverGlue;
import io.github.ygrip.testara.engine.extension.TestaraFrameworkExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResource;
import static org.junit.platform.launcher.EngineFilter.includeEngines;

/**
 * End-to-end regression coverage for the {@code RerunStrategy.DEFERRED} path -- unlike
 * {@code RerunStrategy.IMMEDIATE}, its retry (see {@code DeferredRerunManager#executeScenarioRerun})
 * runs entirely outside JUnit Platform's own node-tree walk, at the very end of the suite, via a
 * raw {@code CucumberExecutionContext} rather than through the discovered {@code PickleDescriptor}'s
 * {@code execute()}. Before the fix under test, this path had three confirmed defects, reproduced
 * from a real IntelliJ run with step notifications enabled:
 * <ol>
 *   <li>{@code DeferredRerunManager} fabricated a brand new, disconnected {@code PickleDescriptor}
 *       for the retried scenario (only coincidentally sharing the same computed unique id with the
 *       real, originally-discovered one) instead of reusing it -- surfacing as a duplicate scenario
 *       node in the tree alongside the original attempt's own node.</li>
 *   <li>That fabricated descriptor never had its own {@code executionStarted}/{@code executionFinished}
 *       reported at all (only its per-step children did, via dynamic registration), so any duration
 *       an IDE showed for it was not derived from a real reported time span.</li>
 *   <li>{@code TestaraCucumberEngineExecutionContext#runTestCase} suppressed all per-step reporting
 *       after a failing step ({@code reportFinalOutcome=false}) whenever ANY rerun mechanism was
 *       merely configured (i.e. {@code isRerunEnabled()}), even for strategies -- like DEFERRED --
 *       where no later attempt exists within the SAME synchronous call to pick up that reporting
 *       job. The failing step's own result and every step after it vanished silently, never even
 *       reported as skipped, on the one attempt that WAS the properly-linked, real scenario node.</li>
 * </ol>
 * This test asserts, through the real JUnit Platform {@link Launcher}, that after the fix: exactly
 * one scenario node is ever reported, its own definitive (deferred rerun) attempt's reported
 * duration reflects real elapsed step time, and every one of its six steps -- including the three
 * after the failure -- gets a definitive terminal report during that definitive attempt.
 */
class DeferredRerunEngineTest {

  private static final String FEATURE = "features/deferred_rerun_six_steps.feature";
  private static final String SCENARIO_NAME = "deferred rerun with six steps and a real non-flaky failure";
  private static final String GLUE_PACKAGE = "io.github.ygrip.testara.engine.deferredrerun";

  @BeforeEach
  void resetState() {
    TestaraFrameworkExtension.resetFramework();
    DeferredRerunGlue.reset();
  }

  @AfterEach
  void cleanup() {
    TestaraFrameworkExtension.resetFramework();
  }

  @Test
  void deferredRerunReportsExactlyOneScenarioNodeWithRealDurationAndAllStepsAccountedFor() {
    Recorder recorder = execute();

    // The scenario really was retried through the deferred-rerun mechanism under test.
    assertTrue(DeferredRerunGlue.STEP_FOUR_INVOCATIONS.get() >= 2,
        "the always-failing step must have run at least twice (original attempt + deferred rerun), ran "
            + DeferredRerunGlue.STEP_FOUR_INVOCATIONS.get() + " time(s)");

    // --- Defect 1: exactly one logical scenario node -- never a fabricated, disconnected duplicate ---
    Set<UniqueId> scenarioIds = recorder.events.stream()
        .filter(event -> "scenario".equals(event.identifier.getUniqueIdObject().getLastSegment().getType()))
        .filter(event -> SCENARIO_NAME.equals(event.identifier.getDisplayName()))
        .map(event -> event.identifier.getUniqueIdObject())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(1, scenarioIds.size(),
        "the scenario must be reported under exactly one unique id across the original attempt and "
            + "every deferred rerun attempt (no second, disconnected descriptor), found: " + scenarioIds);
    UniqueId scenarioId = scenarioIds.iterator().next();

    List<Event> scenarioStarted = recorder.eventsFor(Kind.STARTED, scenarioId);
    List<Event> scenarioFinished = recorder.eventsFor(Kind.FINISHED, scenarioId);
    assertTrue(scenarioStarted.size() >= 2,
        "expected the original attempt and at least one deferred rerun attempt to each report their "
            + "own start against the SAME scenario node, found " + scenarioStarted.size());
    assertEquals(scenarioStarted.size(), scenarioFinished.size(), "every reported start must have a matching finish");

    // --- Defect 2: the deferred rerun's own attempt reports a real, non-near-zero duration ---
    Event lastStart = scenarioStarted.get(scenarioStarted.size() - 1);
    Event lastFinish = scenarioFinished.get(scenarioFinished.size() - 1);
    long gapNanos = lastFinish.nanos - lastStart.nanos;
    long sleepNanos = TimeUnit.MILLISECONDS.toNanos(DeferredRerunGlue.STEP_TWO_SLEEP_MILLIS);
    assertTrue(gapNanos >= sleepNanos * 3 / 4,
        "the deferred rerun attempt's own reported span must reflect real elapsed step time (it must "
            + "actually cover the real step-two sleep), gap(ms)=" + TimeUnit.NANOSECONDS.toMillis(gapNanos));
    assertEquals(TestExecutionResult.Status.FAILED, lastFinish.result.getStatus(),
        "the scenario keeps genuinely failing on retry, so the final reported outcome must be FAILED, "
            + "never silently reported as passed or swallowed");

    // --- Defect 3: every step -- including all three after the failure -- gets a definitive status
    // on the deferred rerun's own (final/definitive) attempt, not silently dropped. ---
    List<String> passingSteps = List.of("deferred step one passes", "deferred step two sleeps and passes",
        "deferred step three passes");
    for (String step : passingSteps) {
      assertEquals(1, recorder.count(Kind.DYNAMIC, step), step + " must be dynamically registered exactly once");
      assertEquals(1, recorder.countAtOrAfter(Kind.FINISHED, step, lastStart.nanos),
          step + " must be reported finished exactly once during the definitive (deferred rerun) attempt");
    }

    String failingStep = "deferred step four always fails";
    assertEquals(1, recorder.count(Kind.DYNAMIC, failingStep));
    assertEquals(1, recorder.countAtOrAfter(Kind.FINISHED, failingStep, lastStart.nanos),
        "the failing step must be reported finished exactly once during the definitive attempt");

    List<String> skippedSteps =
        List.of("deferred step five is never executed", "deferred step six is never executed");
    for (String step : skippedSteps) {
      assertEquals(1, recorder.count(Kind.DYNAMIC, step), step + " must be dynamically registered exactly once");
      assertEquals(1, recorder.countAtOrAfter(Kind.SKIPPED, step, lastStart.nanos),
          step + " must be reported skipped exactly once during the definitive (deferred rerun) attempt "
              + "-- this is the exact 'steps missing' bug: they must never silently vanish");
    }
  }

  // ===================================================================================
  // Parallel/virtual-thread coverage. The original attempt of a DEFERRED scenario reports its own,
  // real failure through the descriptor as usual (JUnit Platform's own automatic wrapping of its
  // failing execute()); the deferred retry -- run later, at the very end of the run, from
  // finishTestRun -- reuses the SAME, real, originally-discovered descriptor/UniqueId and reports
  // its own outcome separately (see DeferredRerunManager#executeScenarioRerun). That means a scenario
  // which needs a deferred retry is, quite deliberately, reported TWICE for the same UniqueId across
  // the whole run: once for the original failing attempt, once for the retry. This is an accepted,
  // long-standing characteristic of this design (not something this engine merges into one report),
  // and is unaffected by which execution model (sequential, platform threads, or virtual threads) is
  // configured.
  // ===================================================================================

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void deferredRerunUnderVirtualThreadsReusesSameDescriptorAndReportsBothAttemptsWithRealDurations() {
    DeferredRerunRecoverGlue.reset();

    Recorder recorder = execute("features/deferred_rerun_recovers.feature",
        "io.github.ygrip.testara.engine.deferredrerun.recover",
        Map.of("cucumber.execution.parallel.enabled", "true",
            "cucumber.execution.parallel.virtual-thread.enabled", "true",
            "cucumber.max.retry.failed.scenarios", "1"));

    assertTrue(DeferredRerunRecoverGlue.STEP_THREE_INVOCATIONS.get() >= 2,
        "the flaky step must have run at least twice (original attempt + deferred rerun), ran "
            + DeferredRerunRecoverGlue.STEP_THREE_INVOCATIONS.get() + " time(s)");

    // --- Same descriptor/UniqueId reused for the retry (the object-identity fix) ---
    String scenarioName = "deferred rerun recovers after one real failure";
    Set<UniqueId> scenarioIds = recorder.events.stream()
        .filter(event -> "scenario".equals(event.identifier.getUniqueIdObject().getLastSegment().getType()))
        .filter(event -> scenarioName.equals(event.identifier.getDisplayName()))
        .map(event -> event.identifier.getUniqueIdObject())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(1, scenarioIds.size(), "must be reported under exactly one unique id, found: " + scenarioIds);
    UniqueId scenarioId = scenarioIds.iterator().next();

    // --- Two report pairs for that same id: the original attempt's own automatic report (FAILED)
    // plus the deferred retry's manual report (SUCCESSFUL) -- an accepted characteristic, not a bug,
    // now that the CONTAINER type fix (rather than merging reports) is what keeps this from rendering
    // as a structurally-forced duplicate row in an IDE.
    List<Event> started = recorder.eventsFor(Kind.STARTED, scenarioId);
    List<Event> finished = recorder.eventsFor(Kind.FINISHED, scenarioId);
    assertEquals(2, started.size(), "expected one start from the original attempt and one from the "
        + "deferred retry against the SAME scenario node, found " + started.size());
    assertEquals(2, finished.size(), "expected one finish from the original attempt and one from the "
        + "deferred retry, found " + finished.size());

    Event lastFinish = finished.get(finished.size() - 1);
    Event lastStart = started.get(started.size() - 1);
    assertEquals(TestExecutionResult.Status.SUCCESSFUL, lastFinish.result.getStatus(),
        "the scenario ultimately passes on retry, so the LAST reported outcome must be SUCCESSFUL");

    // --- The deferred retry's own reported span reflects real elapsed step time (not fabricated) ---
    long gapNanos = lastFinish.nanos - lastStart.nanos;
    long sleepNanos = TimeUnit.MILLISECONDS.toNanos(DeferredRerunRecoverGlue.STEP_TWO_SLEEP_MILLIS);
    assertTrue(gapNanos >= sleepNanos * 3 / 4,
        "the deferred retry's own reported span must reflect real elapsed step time, gap(ms)="
            + TimeUnit.NANOSECONDS.toMillis(gapNanos));

    // --- The flaky step is reported once FAILED (the original attempt's own, real, live result --
    // DEFERRED never suppresses its original attempt's step results, unlike IMMEDIATE/COMBINE) and
    // once SUCCESSFUL (the retry's own result).
    String flakyStep = "recover step three fails once then passes";
    long failedCount = recorder.events.stream()
        .filter(event -> event.kind == Kind.FINISHED && flakyStep.equals(event.identifier.getDisplayName()))
        .filter(event -> event.result.getStatus() == TestExecutionResult.Status.FAILED)
        .count();
    long successfulCount = recorder.events.stream()
        .filter(event -> event.kind == Kind.FINISHED && flakyStep.equals(event.identifier.getDisplayName()))
        .filter(event -> event.result.getStatus() == TestExecutionResult.Status.SUCCESSFUL)
        .count();
    assertEquals(1, failedCount, "the original attempt's real failing step report must reach the listener");
    assertEquals(1, successfulCount, "the retry's own passing step report must reach the listener");
  }

  private Recorder execute() {
    LauncherDiscoveryRequest request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClasspathResource(FEATURE))
        .filters(includeEngines("testara-cucumber"))
        .configurationParameter("cucumber.glue", GLUE_PACKAGE)
        .configurationParameter("cucumber.object-factory",
            "io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory")
        .configurationParameter("cucumber.step.notifications.enabled", "true")
        .configurationParameter("cucumber.rerun.strategy", "DEFERRED")
        .configurationParameter("cucumber.max.retry.failed.scenarios", "1")
        .configurationParameter("custom.report.enabled", "false")
        .build();

    Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
        .enableLauncherSessionListenerAutoRegistration(false)
        .enablePostDiscoveryFilterAutoRegistration(false)
        .build());

    Recorder recorder = new Recorder();
    launcher.execute(request, recorder);
    return recorder;
  }

  /**
   * Parameterized variant used by the parallel/virtual-thread test below: a different feature/glue
   * pair, plus arbitrary extra configuration parameters (virtual threads, bounded parallelism, ...)
   * layered on top of the same DEFERRED-strategy baseline the single-scenario test above uses.
   */
  private Recorder execute(String featurePath, String gluePackage, Map<String, String> extraConfig) {
    LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClasspathResource(featurePath))
        .filters(includeEngines("testara-cucumber"))
        .configurationParameter("cucumber.glue", gluePackage)
        .configurationParameter("cucumber.object-factory",
            "io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory")
        .configurationParameter("cucumber.step.notifications.enabled", "true")
        .configurationParameter("cucumber.rerun.strategy", "DEFERRED")
        .configurationParameter("custom.report.enabled", "false");
    extraConfig.forEach(builder::configurationParameter);
    LauncherDiscoveryRequest request = builder.build();

    Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
        .enableLauncherSessionListenerAutoRegistration(false)
        .enablePostDiscoveryFilterAutoRegistration(false)
        .build());

    Recorder recorder = new Recorder();
    launcher.execute(request, recorder);
    return recorder;
  }

  private enum Kind {DYNAMIC, STARTED, FINISHED, SKIPPED}

  private static final class Event {
    final Kind kind;
    final TestIdentifier identifier;
    final TestExecutionResult result;
    final long nanos;

    Event(Kind kind, TestIdentifier identifier, TestExecutionResult result) {
      this.kind = kind;
      this.identifier = identifier;
      this.result = result;
      this.nanos = System.nanoTime();
    }
  }

  private static final class Recorder implements TestExecutionListener {
    private final List<Event> events = new ArrayList<>();

    @Override
    public synchronized void dynamicTestRegistered(TestIdentifier testIdentifier) {
      events.add(new Event(Kind.DYNAMIC, testIdentifier, null));
    }

    @Override
    public synchronized void executionStarted(TestIdentifier testIdentifier) {
      events.add(new Event(Kind.STARTED, testIdentifier, null));
    }

    @Override
    public synchronized void executionSkipped(TestIdentifier testIdentifier, String reason) {
      events.add(new Event(Kind.SKIPPED, testIdentifier, null));
    }

    @Override
    public synchronized void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
      events.add(new Event(Kind.FINISHED, testIdentifier, testExecutionResult));
    }

    synchronized long count(Kind kind, String displayName) {
      return events.stream()
          .filter(event -> event.kind == kind && displayName.equals(event.identifier.getDisplayName()))
          .count();
    }

    synchronized long countAtOrAfter(Kind kind, String displayName, long nanosThreshold) {
      return events.stream()
          .filter(event -> event.kind == kind && displayName.equals(event.identifier.getDisplayName()))
          .filter(event -> event.nanos >= nanosThreshold)
          .count();
    }

    synchronized List<Event> eventsFor(Kind kind, UniqueId uniqueId) {
      return events.stream()
          .filter(event -> event.kind == kind && event.identifier.getUniqueIdObject().equals(uniqueId))
          .collect(Collectors.toList());
    }
  }
}
