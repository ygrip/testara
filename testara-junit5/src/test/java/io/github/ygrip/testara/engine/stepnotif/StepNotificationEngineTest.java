package io.github.ygrip.testara.engine.stepnotif;

import io.github.ygrip.testara.engine.extension.TestaraFrameworkExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResource;
import static org.junit.platform.launcher.EngineFilter.includeEngines;

/**
 * End-to-end coverage for step-level notifications driven through the real JUnit Platform
 * {@link Launcher}, which is the only way to exercise (and regression-guard) the framework's
 * automatic re-execution of static children — the root cause of the original double-reporting bug.
 * <p>
 * Runs a two-scenario feature (one all-passing, one where the 2nd of 3 steps fails) with step
 * notifications enabled and asserts:
 * <ul>
 *   <li>each step produces exactly one started/finished (or skipped) event — no duplicates,</li>
 *   <li>real per-step status: pass/fail/skip, with the real thrown assertion error,</li>
 *   <li>real per-step duration (the sleeping step's reported gap is at least the sleep),</li>
 *   <li>the scenario itself is reported FAILED when a step fails.</li>
 * </ul>
 * A regression variant with notifications disabled asserts no step nodes appear while the
 * scenario-level pass/fail reporting is unchanged.
 */
class StepNotificationEngineTest {

  private static final String FEATURE = "features/step_notifications.feature";
  private static final String NINE_STEP_FEATURE = "features/step_notifications_nine_steps.feature";
  private static final String GLUE_PACKAGE = "io.github.ygrip.testara.engine.stepnotif";

  private static final String PASS_S1 = "a first passing step";
  private static final String PASS_S2 = "a second passing step";
  private static final String PASS_S3 = "a third passing step";
  private static final String FAIL_S1 = "first step sleeps and passes";
  private static final String FAIL_S2 = "second step fails";
  private static final String FAIL_S3 = "third step is never executed";

  @BeforeEach
  void resetState() {
    TestaraFrameworkExtension.resetFramework();
    StepNotificationGlue.reset();
  }

  @AfterEach
  void cleanup() {
    TestaraFrameworkExtension.resetFramework();
  }

  @Test
  void everyStepIsReportedOnceWithRealStatusDurationAndScenarioFailure() {
    Recorder recorder = execute(true);

    // --- Bug A: exactly one started + one finished per step (no framework re-execution) ---
    assertEquals(1, recorder.count(Kind.STARTED, FAIL_S1), "step 1 must start exactly once");
    assertEquals(1, recorder.count(Kind.FINISHED, FAIL_S1), "step 1 must finish exactly once");
    assertEquals(1, recorder.count(Kind.STARTED, FAIL_S2), "failing step must start exactly once");
    assertEquals(1, recorder.count(Kind.FINISHED, FAIL_S2), "failing step must finish exactly once");

    // Each step is dynamically registered exactly once.
    assertEquals(1, recorder.count(Kind.DYNAMIC, FAIL_S1));
    assertEquals(1, recorder.count(Kind.DYNAMIC, FAIL_S2));
    assertEquals(1, recorder.count(Kind.DYNAMIC, FAIL_S3));

    // --- Real per-step status ---
    assertEquals(TestExecutionResult.Status.SUCCESSFUL,
        recorder.finishStatus(FAIL_S1),
        "first step must be reported successful");

    assertEquals(TestExecutionResult.Status.FAILED,
        recorder.finishStatus(FAIL_S2),
        "second step must be reported failed");
    Optional<Throwable> failure = recorder.finishThrowable(FAIL_S2);
    assertTrue(failure.isPresent(), "failed step must carry the real thrown error");
    assertTrue(failure.get().getMessage() != null
            && failure.get().getMessage().contains(StepNotificationGlue.FAILURE_MESSAGE),
        "failed step must carry the real thrown assertion message, was: " + failure.map(Throwable::getMessage));

    // --- Bug D: the step after the failure is skipped, not started/finished ---
    assertEquals(1, recorder.count(Kind.SKIPPED, FAIL_S3), "third step must be reported skipped");
    assertEquals(0, recorder.count(Kind.STARTED, FAIL_S3), "skipped step must not be started");
    assertEquals(0, recorder.count(Kind.FINISHED, FAIL_S3), "skipped step must not be finished");
    assertFalse(StepNotificationGlue.SKIPPED_STEP_INVOKED.get(), "skipped step body must never run");

    // --- Real per-step duration: the sleeping step's reported gap reflects real elapsed time ---
    long gapNanos = recorder.finishNanos(FAIL_S1) - recorder.startNanos(FAIL_S1);
    long sleepNanos = TimeUnit.MILLISECONDS.toNanos(StepNotificationGlue.FIRST_STEP_SLEEP_MILLIS);
    // Allow a little slack below the exact sleep to absorb clock granularity, but far above the
    // near-zero framework-overhead duration the old fabricated second report produced.
    assertTrue(gapNanos >= sleepNanos * 3 / 4,
        "sleeping step duration must reflect real elapsed time, gap(ms)=" + TimeUnit.NANOSECONDS.toMillis(gapNanos));

    // --- Passing scenario: each step reported exactly once as successful, none skipped ---
    for (String step : List.of(PASS_S1, PASS_S2, PASS_S3)) {
      assertEquals(1, recorder.count(Kind.STARTED, step), step + " must start once");
      assertEquals(1, recorder.count(Kind.FINISHED, step), step + " must finish once");
      assertEquals(0, recorder.count(Kind.SKIPPED, step), step + " must not be skipped");
      assertEquals(TestExecutionResult.Status.SUCCESSFUL, recorder.finishStatus(step), step + " must pass");
    }

    // --- Bug B: scenario-level result mirrors the step outcomes ---
    List<TestIdentifier> failedScenarios = recorder.finishedScenarios(TestExecutionResult.Status.FAILED);
    List<TestIdentifier> passedScenarios = recorder.finishedScenarios(TestExecutionResult.Status.SUCCESSFUL);
    assertEquals(1, failedScenarios.size(), "exactly one scenario must report FAILED");
    assertEquals(1, passedScenarios.size(), "exactly one scenario must report SUCCESSFUL");

    // The failing step must belong to the FAILED scenario.
    UniqueId failedScenarioId = failedScenarios.get(0).getUniqueIdObject();
    assertEquals(Optional.of(failedScenarioId), recorder.parentOf(FAIL_S2),
        "failing step must be nested under the failed scenario");
  }

  @Test
  void withNotificationsDisabledNoStepNodesAppearButScenarioResultsAreUnchanged() {
    Recorder recorder = execute(false);

    assertTrue(recorder.stepIdentifiers().isEmpty(),
        "no step-level nodes must be registered when notifications are disabled, found: "
            + recorder.stepIdentifiers());

    assertEquals(1, recorder.finishedScenarios(TestExecutionResult.Status.FAILED).size(),
        "failing scenario must still report FAILED");
    assertEquals(1, recorder.finishedScenarios(TestExecutionResult.Status.SUCCESSFUL).size(),
        "passing scenario must still report SUCCESSFUL");
  }

  @Test
  void allNineStepsAreReportedExactlyOnceAcrossAMidScenarioFailureWithADataTableStep() {
    Recorder recorder = execute(true, NINE_STEP_FEATURE);

    List<String> passingSteps = List.of("a data table step", "step two passes", "step three passes");
    for (String step : passingSteps) {
      assertEquals(1, recorder.count(Kind.DYNAMIC, step), step + " must be dynamically registered exactly once");
      assertEquals(1, recorder.count(Kind.STARTED, step), step + " must start exactly once");
      assertEquals(1, recorder.count(Kind.FINISHED, step), step + " must finish exactly once");
      assertEquals(TestExecutionResult.Status.SUCCESSFUL, recorder.finishStatus(step), step + " must pass");
    }

    String failingStep = "step four fails";
    assertEquals(1, recorder.count(Kind.DYNAMIC, failingStep));
    assertEquals(1, recorder.count(Kind.STARTED, failingStep), "failing step must start exactly once");
    assertEquals(1, recorder.count(Kind.FINISHED, failingStep), "failing step must finish exactly once");
    assertEquals(TestExecutionResult.Status.FAILED, recorder.finishStatus(failingStep));
    Optional<Throwable> failure = recorder.finishThrowable(failingStep);
    assertTrue(failure.isPresent(), "failing step must carry the real thrown error");
    assertTrue(failure.get().getMessage() != null
            && failure.get().getMessage().contains(StepNotificationGlue.NINE_STEP_FAILURE_MESSAGE),
        "failing step must carry the real assertion message, was: " + failure.map(Throwable::getMessage));

    // --- Every step after the failure must still appear in the tree, marked skipped, exactly once ---
    List<String> skippedSteps = List.of("step five is never executed", "step six is never executed",
        "step seven is never executed", "step eight is never executed", "step nine is never executed");
    for (String step : skippedSteps) {
      assertEquals(1, recorder.count(Kind.DYNAMIC, step), step + " must be dynamically registered exactly once");
      assertEquals(1, recorder.count(Kind.SKIPPED, step), step + " must be reported skipped exactly once");
      assertEquals(0, recorder.count(Kind.STARTED, step), "skipped step must never start: " + step);
      assertEquals(0, recorder.count(Kind.FINISHED, step), "skipped step must never finish: " + step);
    }
    assertTrue(StepNotificationGlue.NINE_STEP_INVOKED_STEPS.containsAll(List.of("one", "two", "three", "four")),
        "steps one through four must actually have run");
    for (String step : List.of("five", "six", "seven", "eight", "nine")) {
      assertFalse(StepNotificationGlue.NINE_STEP_INVOKED_STEPS.contains(step),
          "skipped step body must never run: " + step);
    }

    // --- The scenario-level descriptor itself must be reported exactly once by OUR code, i.e. no
    // double dispatch of executionStarted/executionFinished for the PickleDescriptor. It must also
    // report as a plain CONTAINER (not CONTAINER_AND_TEST): IntelliJ's JUnit5 test-tree renders a
    // CONTAINER_AND_TEST node as two separate rows by design (one as a leaf test, one as a container
    // with children) -- that IDE rendering behavior, not a reporting/timing defect, was the actual
    // cause of a scenario appearing twice in the tree. CONTAINER still self-reports its own execution
    // result correctly (JUnit Platform's NodeTestTask does not branch on Type when executing or
    // reporting a node), and is still discovered as containing tests despite having no static step
    // children, because PickleDescriptor#mayRegisterTests() returns true. ---
    List<Event> scenarioStarted = recorder.events.stream()
        .filter(event -> event.kind == Kind.STARTED
            && "scenario".equals(event.identifier.getUniqueIdObject().getLastSegment().getType())
            && event.identifier.getDisplayName()
                .equals("nine steps with a data table argument and a mid-scenario failure"))
        .collect(Collectors.toList());
    List<Event> scenarioFinished = recorder.events.stream()
        .filter(event -> event.kind == Kind.FINISHED
            && "scenario".equals(event.identifier.getUniqueIdObject().getLastSegment().getType())
            && event.identifier.getDisplayName()
                .equals("nine steps with a data table argument and a mid-scenario failure"))
        .collect(Collectors.toList());
    assertEquals(1, scenarioStarted.size(), "scenario node must be started exactly once by our own engine code");
    assertEquals(1, scenarioFinished.size(), "scenario node must be finished exactly once by our own engine code");
    assertEquals(TestExecutionResult.Status.FAILED, scenarioFinished.get(0).result.getStatus(),
        "scenario must be reported FAILED since step four failed");
    assertEquals(TestDescriptor.Type.CONTAINER, scenarioStarted.get(0).identifier.getType(),
        "a scenario with step notifications enabled must report as CONTAINER, not CONTAINER_AND_TEST, "
            + "so IntelliJ's test-tree does not render it as two structurally-forced rows");
  }

  @Test
  void rerunDoesNotReRegisterTheSameStepDescriptorsAsDynamicTests() {
    // With a rerun configured, the failing scenario runs twice (original attempt + one retry),
    // constructing a brand new TestaraTestCaseResultObserver each time. Each one re-registers its
    // step descriptors' lookup map, but dynamicTestRegistered must still fire exactly once per step
    // for the whole scenario lifetime -- not once per attempt.
    Recorder recorder = execute(true, FEATURE, "1");

    for (String step : List.of(FAIL_S1, FAIL_S2, FAIL_S3)) {
      assertEquals(1, recorder.count(Kind.DYNAMIC, step),
          step + " must be dynamically registered exactly once across all rerun attempts");
    }
  }

  private Recorder execute(boolean stepNotifications) {
    return execute(stepNotifications, FEATURE);
  }

  private Recorder execute(boolean stepNotifications, String featurePath) {
    return execute(stepNotifications, featurePath, "0");
  }

  private Recorder execute(boolean stepNotifications, String featurePath, String maxRetryFailedScenarios) {
    LauncherDiscoveryRequest request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClasspathResource(featurePath))
        .filters(includeEngines("testara-cucumber"))
        .configurationParameter("cucumber.glue", GLUE_PACKAGE)
        .configurationParameter("cucumber.object-factory",
            "io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory")
        .configurationParameter("cucumber.step.notifications.enabled", String.valueOf(stepNotifications))
        .configurationParameter("cucumber.max.retry.failed.scenarios", maxRetryFailedScenarios)
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

    synchronized TestExecutionResult.Status finishStatus(String displayName) {
      return finishEvent(displayName).result.getStatus();
    }

    synchronized Optional<Throwable> finishThrowable(String displayName) {
      return finishEvent(displayName).result.getThrowable();
    }

    synchronized long startNanos(String displayName) {
      return single(Kind.STARTED, displayName).nanos;
    }

    synchronized long finishNanos(String displayName) {
      return finishEvent(displayName).nanos;
    }

    synchronized Optional<UniqueId> parentOf(String displayName) {
      return single(Kind.DYNAMIC, displayName).identifier.getParentIdObject();
    }

    synchronized List<TestIdentifier> stepIdentifiers() {
      return events.stream()
          .map(event -> event.identifier)
          .filter(identifier -> "step".equals(identifier.getUniqueIdObject().getLastSegment().getType()))
          .distinct()
          .collect(Collectors.toList());
    }

    synchronized List<TestIdentifier> finishedScenarios(TestExecutionResult.Status status) {
      return events.stream()
          .filter(event -> event.kind == Kind.FINISHED)
          .filter(event -> "scenario".equals(event.identifier.getUniqueIdObject().getLastSegment().getType()))
          .filter(event -> event.result.getStatus() == status)
          .map(event -> event.identifier)
          .collect(Collectors.toList());
    }

    private Event finishEvent(String displayName) {
      return single(Kind.FINISHED, displayName);
    }

    private Event single(Kind kind, String displayName) {
      List<Event> matches = events.stream()
          .filter(event -> event.kind == kind && displayName.equals(event.identifier.getDisplayName()))
          .collect(Collectors.toList());
      assertEquals(1, matches.size(), "expected exactly one " + kind + " event for step '" + displayName + "'");
      return matches.get(0);
    }
  }
}
