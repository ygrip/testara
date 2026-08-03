package io.github.ygrip.testara.engine.stepnotif;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plain Cucumber glue used by the end-to-end step-notification engine test. Isolated in its own
 * package so {@code cucumber.glue} can target only these step definitions.
 */
public class StepNotificationGlue {

  public static final long FIRST_STEP_SLEEP_MILLIS = 200L;
  public static final String FAILURE_MESSAGE = "boom from the second step";
  public static final AtomicBoolean SKIPPED_STEP_INVOKED = new AtomicBoolean(false);

  // Nine-step scenario (mirrors the reported bug shape: a leading step with a DataTable argument,
  // then a mid-scenario failure that must not swallow the reporting of the remaining steps).
  public static final String NINE_STEP_FAILURE_MESSAGE = "boom from step four";
  public static final Set<String> NINE_STEP_INVOKED_STEPS = ConcurrentHashMap.newKeySet();

  public static void reset() {
    SKIPPED_STEP_INVOKED.set(false);
    NINE_STEP_INVOKED_STEPS.clear();
  }

  @Given("a first passing step")
  public void aFirstPassingStep() {
  }

  @When("a second passing step")
  public void aSecondPassingStep() {
  }

  @Then("a third passing step")
  public void aThirdPassingStep() {
  }

  @Given("first step sleeps and passes")
  public void firstStepSleepsAndPasses() throws InterruptedException {
    Thread.sleep(FIRST_STEP_SLEEP_MILLIS);
  }

  @When("second step fails")
  public void secondStepFails() {
    fail(FAILURE_MESSAGE);
  }

  @Then("third step is never executed")
  public void thirdStepIsNeverExecuted() {
    SKIPPED_STEP_INVOKED.set(true);
  }

  @Given("a data table step")
  public void aDataTableStep(DataTable table) {
    NINE_STEP_INVOKED_STEPS.add("one");
  }

  @When("step two passes")
  public void stepTwoPasses() {
    NINE_STEP_INVOKED_STEPS.add("two");
  }

  @Then("step three passes")
  public void stepThreePasses() {
    NINE_STEP_INVOKED_STEPS.add("three");
  }

  @When("step four fails")
  public void stepFourFails() {
    NINE_STEP_INVOKED_STEPS.add("four");
    fail(NINE_STEP_FAILURE_MESSAGE);
  }

  @Then("step five is never executed")
  public void stepFiveIsNeverExecuted() {
    NINE_STEP_INVOKED_STEPS.add("five");
  }

  @Then("step six is never executed")
  public void stepSixIsNeverExecuted() {
    NINE_STEP_INVOKED_STEPS.add("six");
  }

  @Then("step seven is never executed")
  public void stepSevenIsNeverExecuted() {
    NINE_STEP_INVOKED_STEPS.add("seven");
  }

  @Then("step eight is never executed")
  public void stepEightIsNeverExecuted() {
    NINE_STEP_INVOKED_STEPS.add("eight");
  }

  @Then("step nine is never executed")
  public void stepNineIsNeverExecuted() {
    NINE_STEP_INVOKED_STEPS.add("nine");
  }
}
