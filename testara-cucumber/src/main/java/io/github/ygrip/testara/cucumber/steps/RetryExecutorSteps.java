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

  @When("{actor} wait for {long} {timeUnit}")
  public void whenWaitFor(String identifier, long wait, TimeUnit timeUnit) {
    Awaitility.await()
        .pollInSameThread()
        .timeout(wait + 1, timeUnit)
        .pollDelay(wait, timeUnit)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(true, equalTo(true)));
  }

  @When("{actor} start conditional retry")
  public void startConditionalRetry(String identifier) {
    collector.startCollecting();
  }

  @When("{actor} execute conditional retry")
  public void executeConditionalRetry(String identifier) throws Throwable {
    boolean success = collector.executeAll();
    assertThat("Conditional retry failed", success, equalTo(true));
  }

  @When("{actor} execute conditional retry with interval of {long} {timeUnit} at most {long} {timeUnit}")
  public void executeConditionalRetry(String identifier,
      long interval,
      TimeUnit intervalUnit,
      long timeout,
      TimeUnit timeoutUnit) throws Throwable {
    boolean success = collector.executeAll(Duration.of(timeout, DurationParser.toTemporalUnit(timeoutUnit)),
        Duration.of(interval, DurationParser.toTemporalUnit(intervalUnit)));
    assertThat(String.format("Conditional retry failed after %s",
        Duration.of(interval, DurationParser.toTemporalUnit(intervalUnit))), success, equalTo(true));
  }

  @When("{actor} execute conditional retry at most {int} attempts")
  public void executeConditionalRetry(String identifier, Integer attempts) throws Throwable {
    collector.executeAtMost(attempts);
  }
}
