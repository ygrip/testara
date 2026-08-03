package io.github.ygrip.testara.engine.deferredrerun;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plain Cucumber glue used by the end-to-end deferred-rerun engine test. Isolated in its own
 * package so {@code cucumber.glue} can target only these step definitions, and so its scenario's
 * classpath resource URI never collides with any other test's feature file (important because
 * {@code FailedScenariosListener} is a process-wide singleton that never forgets a scenario that
 * keeps failing -- see the test class javadoc for details).
 */
public class DeferredRerunGlue {

  public static final long STEP_TWO_SLEEP_MILLIS = 150L;
  public static final String FAILURE_MESSAGE = "boom from deferred step four";
  // Counts every invocation of the always-failing step, across the original attempt and every
  // deferred rerun attempt, so a test can assert the scenario really was retried.
  public static final AtomicInteger STEP_FOUR_INVOCATIONS = new AtomicInteger(0);
  public static final Set<String> INVOKED_STEPS = ConcurrentHashMap.newKeySet();

  public static void reset() {
    STEP_FOUR_INVOCATIONS.set(0);
    INVOKED_STEPS.clear();
  }

  @Given("deferred step one passes")
  public void stepOne() {
    INVOKED_STEPS.add("one");
  }

  @When("deferred step two sleeps and passes")
  public void stepTwo() throws InterruptedException {
    INVOKED_STEPS.add("two");
    Thread.sleep(STEP_TWO_SLEEP_MILLIS);
  }

  @Then("deferred step three passes")
  public void stepThree() {
    INVOKED_STEPS.add("three");
  }

  @And("deferred step four always fails")
  public void stepFour() {
    INVOKED_STEPS.add("four");
    STEP_FOUR_INVOCATIONS.incrementAndGet();
    // Deterministic, real (non-flaky) failure: every attempt -- the original AND every deferred
    // retry -- fails identically, mirroring a real assertion/UI failure that a retry cannot fix.
    fail(FAILURE_MESSAGE);
  }

  @And("deferred step five is never executed")
  public void stepFive() {
    INVOKED_STEPS.add("five");
  }

  @And("deferred step six is never executed")
  public void stepSix() {
    INVOKED_STEPS.add("six");
  }
}
