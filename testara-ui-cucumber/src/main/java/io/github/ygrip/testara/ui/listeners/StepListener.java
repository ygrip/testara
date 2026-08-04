package io.github.ygrip.testara.ui.listeners;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestStepStarted;
import io.github.ygrip.testara.ui.context.StepContext;


public final class StepListener implements ConcurrentEventListener {

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestStepStarted.class, this::handleStep);
    publisher.registerHandlerFor(TestCaseFinished.class, this::handleScenario);
  }

  private void handleStep(TestStepStarted event) {
    // Ignore hooks (Before/After)
    if (!(event.getTestStep() instanceof PickleStepTestStep step)) {
      return;
    }
    String stepText = step.getStep()
      .getText();

    StepContext.setStepName(stepText);
  }

  private void handleScenario(TestCaseFinished event) {
    // access scenario info here
    StepContext.setScenarioName(event.getTestCase()
      .getName());
  }
}
