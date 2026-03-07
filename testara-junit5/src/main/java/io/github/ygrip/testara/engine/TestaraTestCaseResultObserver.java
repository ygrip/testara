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
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.opentest4j.TestAbortedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TestaraTestCaseResultObserver implements AutoCloseable {
  private final TestCaseResultObserver delegate;
  private final TestaraNodeDescriptor testDescriptor;
  private final EngineExecutionListener listener;
  private final EventHandler<TestStepStarted> testStepStarted = this::handleTestStepStarted;
  @Getter
  private Result result;
  private final EventHandler<TestCaseFinished> testCaseFinished = this::handleTestCaseFinished;
  private boolean rerun;
  private final EventHandler<TestStepFinished> testStepFinished = this::handleTestStepFinished;

  private TestaraTestCaseResultObserver(EventPublisher bus,
      TestaraNodeDescriptor testDescriptor,
      EngineExecutionListener listener,
      boolean verbose) {
    this.delegate = new TestCaseResultObserver(bus);
    this.testDescriptor = testDescriptor;
    this.listener = listener;
    this.rerun = false;
    bus.registerHandlerFor(TestCaseFinished.class, this.testCaseFinished);
    if (verbose) {
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

  public TestaraTestCaseResultObserver isRerun(boolean rerun) {
    this.rerun = rerun;
    return this;
  }

  private void handleTestStepStarted(TestStepStarted testStepStarted) {
    TestStep testStep = testStepStarted.getTestStep();
    if (testStep instanceof PickleStepTestStep) {
      Step step = ((PickleStepTestStep) testStep).getStep();
      UniqueId uniqueId = this.testDescriptor.getOrigin().stepSegment(testDescriptor.getUniqueId(), step.getLocation());
      Optional<TestaraNodeDescriptor> child = this.testDescriptor.findChildById(uniqueId);
      child.ifPresent(this.listener::executionStarted);
    }
  }

  private void handleTestStepFinished(TestStepFinished testStepFinished) {
    TestStep testStep = testStepFinished.getTestStep();
    if (testStep instanceof PickleStepTestStep) {
      Step step = ((PickleStepTestStep) testStep).getStep();
      UniqueId uniqueId = this.testDescriptor.getOrigin().stepSegment(testDescriptor.getUniqueId(), step.getLocation());
      TestExecutionResult testExecutionResult;
      boolean shouldReport;
      Throwable error = testStepFinished.getResult().getError();
      if (testStepFinished.getResult().getStatus().is(Status.PASSED)) {
        testExecutionResult = TestExecutionResult.successful();
        shouldReport = true;
      } else if (testStepFinished.getResult().getStatus().is(Status.FAILED)) {
        testExecutionResult = TestExecutionResult.failed(ExceptionHandler.trimStackTrace(error));
        shouldReport = this.rerun;
      } else {
        testExecutionResult = TestExecutionResult.aborted(ExceptionHandler.trimStackTrace(error));
        shouldReport = this.rerun;
      }
      if (shouldReport) {
        Optional<TestaraNodeDescriptor> child = this.testDescriptor.findChildById(uniqueId);
        if (child.isPresent()) {
          this.listener.executionFinished(child.get(), testExecutionResult);
          if (!testStepFinished.getResult().getStatus().is(Status.PASSED)) {
            int index = new ArrayList<>(this.testDescriptor.getChildren()).indexOf(child.get());
            int size = this.testDescriptor.getChildren().size();
            List<TestDescriptor> skipped = index <= size - 1 ?
                new ArrayList<>(this.testDescriptor.getChildren()
                    .stream()
                    .map(node -> (TestDescriptor) node)
                    .collect(Collectors.toList())).subList(index, size - 1) :
                Collections.emptyList();
            skipped.forEach(skip -> this.listener.executionSkipped(skip, "Test ignored."));
          }
        }
      }
    }
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

  public void close() {
    this.delegate.close();
  }
}
