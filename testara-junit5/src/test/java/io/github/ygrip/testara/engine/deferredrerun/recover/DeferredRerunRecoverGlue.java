package io.github.ygrip.testara.engine.deferredrerun.recover;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plain Cucumber glue for the single-scenario block-and-merge "recovers on retry" test. Isolated in
 * its own package (see {@code DeferredRerunGlue}'s javadoc for why) so {@code cucumber.glue} can
 * target only these step definitions.
 */
public class DeferredRerunRecoverGlue {

  public static final long STEP_TWO_SLEEP_MILLIS = 120L;
  public static final String FAILURE_MESSAGE = "transient failure for recover step three";

  // Counts every invocation of the flaky step, across the original attempt and every deferred
  // rerun attempt: 1 -> fails, >=2 -> passes.
  public static final AtomicInteger STEP_THREE_INVOCATIONS = new AtomicInteger(0);

  public static void reset() {
    STEP_THREE_INVOCATIONS.set(0);
  }

  @Given("recover step one passes")
  public void stepOne() {
    // no-op
  }

  @When("recover step two sleeps briefly")
  public void stepTwo() throws InterruptedException {
    Thread.sleep(STEP_TWO_SLEEP_MILLIS);
  }

  @Then("recover step three fails once then passes")
  public void stepThree() {
    int invocation = STEP_THREE_INVOCATIONS.incrementAndGet();
    if (invocation == 1) {
      fail(FAILURE_MESSAGE);
    }
  }
}
