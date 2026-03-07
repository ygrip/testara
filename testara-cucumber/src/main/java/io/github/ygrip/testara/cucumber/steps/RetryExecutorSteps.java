package io.github.ygrip.testara.cucumber.steps;

import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.function.MethodInvocationCollector;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.time.DurationParser;
import io.cucumber.java.en.When;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@TestComponent(scope = RegistryScope.TEST)
public class RetryExecutorSteps {
  @Inject
  private MethodInvocationCollector collector;

  @When("^(.+) wait for (\\d+) (milliseconds|seconds|minutes|hours)$")
  public void whenWaitFor(String identifier, Long wait, String timeUnitStr) {
    TimeUnit timeUnit = TimeUnit.valueOf(timeUnitStr.trim().toUpperCase());
    Awaitility.await()
        .pollInSameThread()
        .timeout(wait + 1, timeUnit)
        .pollDelay(wait, timeUnit)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(true, equalTo(true)));
  }

  @When("^(.+) start conditional retry$")
  public void startConditionalRetry(String identifier) {
    collector.startCollecting();
  }

  @When("^(.+) execute conditional retry$")
  public void executeConditionalRetry(String identifier) throws Throwable {
    boolean success = collector.executeAll();
    assertThat("Conditional retry failed", success, equalTo(true));
  }

  @When("^(.+) execute conditional retry with interval of (\\d+) (milliseconds|seconds|minutes|hours) at most (\\d+) (milliseconds|seconds|minutes|hours)$")
  public void executeConditionalRetry(String identifier,
      Long interval,
      String intervalUnitStr,
      Long timeout,
      String timeoutUnitStr) throws Throwable {
    TimeUnit intervalUnit = TimeUnit.valueOf(intervalUnitStr.trim().toUpperCase());
    TimeUnit timeoutUnit = TimeUnit.valueOf(timeoutUnitStr.trim().toUpperCase());
    boolean success = collector.executeAll(Duration.of(timeout, DurationParser.toTemporalUnit(timeoutUnit)),
        Duration.of(interval, DurationParser.toTemporalUnit(intervalUnit)));
    assertThat(String.format("Conditional retry failed after %s",
        Duration.of(interval, DurationParser.toTemporalUnit(intervalUnit))), success, equalTo(true));
  }

  @When("^(.+) execute conditional retry at most (\\d+) attempts$")
  public void executeConditionalRetry(String identifier, Integer attempts) throws Throwable {
    collector.executeAtMost(attempts);
  }
}
