package io.github.ygrip.testara.engine;

import io.github.ygrip.testara.engine.error.UndefinedStepException;
import io.github.ygrip.testara.engine.descriptor.TestaraNodeDescriptor;
import io.github.ygrip.testara.engine.support.ExceptionHandler;
import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.runtime.TestCaseResultObserver;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.Step;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.opentest4j.TestAbortedException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Log4j2
public final class TestaraTestCaseResultObserver {
  private final TestCaseResultObserver delegate;
  private final TestaraNodeDescriptor testDescriptor;
  private final EngineExecutionListener listener;
  // Kept so the handlers registered in the constructor can be removed again in finish(): this bus is
  // shared for the entire test run (not recreated per scenario), so a forgotten handler here would
  // keep receiving every subsequent scenario's step events for the lifetime of the run.
  private final EventPublisher bus;
  private final boolean verbose;
  private final EventHandler<TestStepStarted> testStepStarted = this::handleTestStepStarted;
  @Getter
  private Result result;
  private final EventHandler<TestCaseFinished> testCaseFinished = this::handleTestCaseFinished;
  private final EventHandler<TestStepFinished> testStepFinished = this::handleTestStepFinished;
  // Ordered, dynamically-registered step descriptors keyed by their unique id.
  private final Map<UniqueId, TestaraNodeDescriptor> stepDescriptorsById = new LinkedHashMap<>();
  // Step ids for which executionStarted has already been fired (tracked internally on every
  // attempt, regardless of whether that attempt ends up being reported to the listener).
  private final Set<UniqueId> startedSteps = new LinkedHashSet<>();
  // Every step event is forwarded to the listener the instant it happens, preserving real per-step
  // timestamps/durations. Whether a FAILING/non-passed step's real result is shown (vs. suppressed to
  // a neutral placeholder) is gated by reportFinalOutcome, decided up front, before this attempt even
  // runs: this is only ever correct when this attempt's fate is ALREADY certain in advance regardless
  // of its own outcome (IMMEDIATE's own retry chain and COMBINE's immediate phase are ALWAYS
  // superseded by their own next attempt whenever they fail -- that certainty is what makes
  // precomputing this safe here). PASSING steps are always shown live, since an attempt that passes
  // is never followed by a further attempt (the retry chain that would otherwise supersede it stops
  // as soon as one attempt passes).
  private boolean reportFinalOutcome = true;
  // Set once a step finishes with a non-passed status. Cucumber keeps firing step events for the
  // remaining steps with a SKIPPED status; those are reported as skipped rather than
  // executionStarted/executionFinished so they are not rendered as if they actually ran.
  private boolean previousStepFailed = false;

  private TestaraTestCaseResultObserver(EventPublisher bus,
      TestaraNodeDescriptor testDescriptor,
      EngineExecutionListener listener,
      boolean verbose) {
    this.delegate = new TestCaseResultObserver(bus);
    this.testDescriptor = testDescriptor;
    this.listener = listener;
    this.bus = bus;
    this.verbose = verbose;
    bus.registerHandlerFor(TestCaseFinished.class, this.testCaseFinished);
    if (verbose) {
      registerStepDescriptors();
      bus.registerHandlerFor(TestStepStarted.class, this.testStepStarted);
      bus.registerHandlerFor(TestStepFinished.class, this.testStepFinished);
    }
  }

  public static TestaraTestCaseResultObserver observe(EventBus bus,
      TestaraNodeDescriptor testDescriptor,
      EngineExecutionListener listener,
      boolean verbose) {
    return new TestaraTestCaseResultObserver(bus, testDescriptor, listener, verbose);
  }

  /**
   * Whether a FAILING/non-passed step's real result is reported now, or suppressed to a neutral
   * placeholder because a further, superseding attempt is guaranteed to follow this one within the
   * same synchronous retry chain regardless of its own outcome (IMMEDIATE, and COMBINE's immediate
   * phase).
   */
  public TestaraTestCaseResultObserver reportFinalOutcome(boolean reportFinalOutcome) {
    this.reportFinalOutcome = reportFinalOutcome;
    return this;
  }

  /**
   * Register every step of the scenario as a dynamic test up front so the IDE renders the whole
   * step tree immediately, then transitions each node to started/finished/skipped as Cucumber's
   * events fire. The descriptors are parented to (but not static children of) the scenario, so the
   * JUnit Platform framework never auto-executes them — this observer is their only reporting path.
   */
  private void registerStepDescriptors() {
    if (!(this.testDescriptor instanceof TestaraNodeDescriptor.PickleDescriptor)) {
      return;
    }
    TestaraNodeDescriptor.PickleDescriptor pickleDescriptor =
        (TestaraNodeDescriptor.PickleDescriptor) this.testDescriptor;
    // Rerun attempts construct a brand new observer (fresh startedSteps/previousStepFailed state)
    // against the SAME PickleDescriptor/StepDescriptor instances used on the original attempt. Only
    // the very first attempt should announce the steps to the listener via dynamicTestRegistered;
    // later attempts must still populate this observer's own lookup map (used by
    // handleTestStepStarted/handleTestStepFinished below) without re-announcing the same unique ids.
    boolean announce = pickleDescriptor.claimStepAnnouncement();
    for (TestaraNodeDescriptor stepDescriptor : pickleDescriptor.getStepDescriptors()) {
      this.stepDescriptorsById.put(stepDescriptor.getUniqueId(), stepDescriptor);
      if (announce) {
        this.listener.dynamicTestRegistered(stepDescriptor);
      }
    }
  }

  private void handleTestStepStarted(TestStepStarted testStepStarted) {
    try {
      TestStep testStep = testStepStarted.getTestStep();
      if (!(testStep instanceof PickleStepTestStep)) {
        return;
      }
      // Steps after a failure are reported as skipped, not started.
      if (this.previousStepFailed) {
        return;
      }
      TestaraNodeDescriptor descriptor = findStepDescriptor((PickleStepTestStep) testStep);
      if (descriptor != null) {
        this.startedSteps.add(descriptor.getUniqueId());
        this.listener.executionStarted(descriptor);
      }
    } catch (Throwable t) {
      // This handler runs synchronously on Cucumber's own event bus, inline with step execution.
      // Cucumber does not guard handler dispatch: an exception escaping here would propagate out of
      // Runner.runPickle(...) and abort Cucumber's own processing of every remaining step in the
      // scenario, so none of them would ever fire a TestStepStarted/TestStepFinished event at all
      // (not even as skipped). A defect in this reporting path must never be allowed to do that.
      log.warn("Failed to report step start for scenario '{}'", this.testDescriptor.getDisplayName(), t);
    }
  }

  private void handleTestStepFinished(TestStepFinished testStepFinished) {
    try {
      TestStep testStep = testStepFinished.getTestStep();
      if (!(testStep instanceof PickleStepTestStep)) {
        return;
      }
      TestaraNodeDescriptor descriptor = findStepDescriptor((PickleStepTestStep) testStep);
      if (descriptor == null) {
        return;
      }
      Status status = testStepFinished.getResult().getStatus();
      Throwable error = testStepFinished.getResult().getError();
      if (this.startedSteps.contains(descriptor.getUniqueId())) {
        if (status.is(Status.PASSED)) {
          this.listener.executionFinished(descriptor, TestExecutionResult.successful());
        } else {
          this.previousStepFailed = true;
          if (this.reportFinalOutcome) {
            TestExecutionResult result = status.is(Status.FAILED) ?
                TestExecutionResult.failed(ExceptionHandler.trimStackTrace(error)) :
                TestExecutionResult.aborted(ExceptionHandler.trimStackTrace(error));
            this.listener.executionFinished(descriptor, result);
          } else {
            // Non-final attempt in an already-certain-to-be-superseded retry chain (IMMEDIATE or
            // COMBINE's immediate phase): a later attempt is about to reuse this SAME step descriptor
            // and call executionStarted on it again, which is only valid once this attempt's own
            // execution has been properly closed out. Report a neutral, non-alarming ABORTED result
            // rather than suppressing the finish entirely -- otherwise this node is left permanently
            // "running" and the next attempt's executionStarted would fire twice in a row for the
            // same descriptor with no finish in between.
            this.listener.executionFinished(descriptor, TestExecutionResult.aborted(null));
          }
        }
      } else {
        // Step was never started because a previous step failed. Cucumber will not fire another
        // TestStepStarted/TestStepFinished pair for this exact step within THIS attempt, so it must
        // be reported as skipped now -- on every attempt, not only the definitive one -- otherwise a
        // non-final attempt would silently drop this step and everything after it forever.
        this.previousStepFailed = true;
        this.listener.executionSkipped(descriptor, "Skipped: a previous step failed");
      }
    } catch (Throwable t) {
      // See handleTestStepStarted above: never let a reporting defect abort Cucumber's own
      // remaining step dispatch for this scenario.
      log.warn("Failed to report step finish for scenario '{}'", this.testDescriptor.getDisplayName(), t);
    }
  }

  private TestaraNodeDescriptor findStepDescriptor(PickleStepTestStep testStep) {
    Step step = testStep.getStep();
    UniqueId uniqueId =
        this.testDescriptor.getOrigin().stepSegment(this.testDescriptor.getUniqueId(), step.getLocation());
    return this.stepDescriptorsById.get(uniqueId);
  }

  private void handleTestCaseFinished(TestCaseFinished event) {
    this.result = event.getResult();
  }

  public void assertTestCasePassed() {
    this.delegate.assertTestCasePassed(TestAbortedException::new,
        Function.identity(),
        UndefinedStepException::new,
        Function.identity());
  }

  /**
   * Conclude this attempt: unhook this observer's bus handlers -- otherwise every scenario in the
   * run leaks its own handlers into the run-wide, shared event bus (see the field javadoc on {@link
   * #bus}). Every step event has already been reported to the listener live, as it happened, so
   * there is nothing left to flush -- this is purely bus cleanup. Must be called exactly once per
   * attempt.
   */
  public void finish() {
    this.delegate.close();
    // The event bus is shared for the entire test run (it is created once in
    // TestaraCucumberEngineExecutionContext#createCucumberExecutionContext, not per scenario), so
    // handlers registered by this observer must be explicitly removed here. Otherwise every
    // scenario in the run leaks its own testStepStarted/testStepFinished/testCaseFinished handlers,
    // which keep receiving (and discarding, via the unmatched-uniqueId lookups above) every
    // subsequent scenario's step events for the remainder of the run.
    this.bus.removeHandlerFor(TestCaseFinished.class, this.testCaseFinished);
    if (this.verbose) {
      this.bus.removeHandlerFor(TestStepStarted.class, this.testStepStarted);
      this.bus.removeHandlerFor(TestStepFinished.class, this.testStepFinished);
    }
  }
}
