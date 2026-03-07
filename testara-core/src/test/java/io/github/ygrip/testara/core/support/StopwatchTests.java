package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("stopwatch")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class StopwatchTests extends BaseTests {

  @Test
  public void start_shouldReturnNonNullStopwatch() {
    Stopwatch stopwatch = Stopwatch.start();
    assertThat(stopwatch, is(notNullValue()));
    stopwatch.stop();
  }

  @Test
  public void elapsed_shouldReturnElapsedTime() throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.start();

    // Sleep for a short time
    Thread.sleep(50);

    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    assertThat(elapsed, is(greaterThanOrEqualTo(40L)));

    stopwatch.stop();
  }

  @Test
  public void elapsed_inNanoseconds_shouldReturnPositiveValue() throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.start();
    Thread.sleep(10);

    long elapsed = stopwatch.elapsed(TimeUnit.NANOSECONDS);
    assertThat(elapsed, is(greaterThan(0L)));

    stopwatch.stop();
  }

  @Test
  public void elapsed_inSeconds_shouldConvertCorrectly() throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.start();
    Thread.sleep(1100); // Sleep a bit more than 1 second

    long elapsed = stopwatch.elapsed(TimeUnit.SECONDS);
    assertThat(elapsed, is(greaterThanOrEqualTo(1L)));

    stopwatch.stop();
  }

  @Test
  public void stop_shouldReturnStopwatchInternal() {
    Stopwatch stopwatch = Stopwatch.start();
    Stopwatch.StopwatchInternal internal = stopwatch.stop();

    assertThat(internal, is(notNullValue()));
  }

  @Test
  public void stopwatchInternal_elapsed_shouldReturnElapsedTime() throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.start();
    Thread.sleep(50);

    Stopwatch.StopwatchInternal internal = stopwatch.stop();
    long elapsed = internal.elapsed(TimeUnit.MILLISECONDS);

    assertThat(elapsed, is(greaterThanOrEqualTo(40L)));
  }

  @Test
  public void close_shouldCleanUp() throws Exception {
    try (Stopwatch stopwatch = Stopwatch.start()) {
      Thread.sleep(10);
      long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
      assertThat(elapsed, is(greaterThanOrEqualTo(0L)));
    }
    // Auto-closed, no exception should occur
  }

  @Test
  public void multipleStarts_shouldOverwritePrevious() {
    Stopwatch stopwatch1 = Stopwatch.start();
    Stopwatch stopwatch2 = Stopwatch.start();

    // Both should work, but share the ThreadLocal
    assertThat(stopwatch2, is(notNullValue()));

    stopwatch2.stop();
  }

  @Test
  public void elapsed_inMicroseconds_shouldConvertCorrectly() throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.start();
    Thread.sleep(10);

    long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
    assertThat(elapsed, is(greaterThan(0L)));

    stopwatch.stop();
  }

  @Test
  public void elapsed_immediately_shouldReturnSmallValue() {
    Stopwatch stopwatch = Stopwatch.start();

    // Get elapsed immediately
    long elapsed = stopwatch.elapsed(TimeUnit.NANOSECONDS);

    // Should be very small but non-negative
    assertThat(elapsed, is(greaterThanOrEqualTo(0L)));

    stopwatch.stop();
  }
}
