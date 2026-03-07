package io.github.ygrip.testara.core.support;

import java.util.concurrent.TimeUnit;

public final class Stopwatch implements AutoCloseable {

  private static final ThreadLocal<StopwatchInternal> WATCH = new ThreadLocal<>();

  private Stopwatch() {
    // utility class
  }

  public static Stopwatch start() {
    StopwatchInternal internal = new StopwatchInternal(System.nanoTime());
    WATCH.set(internal);
    return new Stopwatch();
  }

  public long elapsed(TimeUnit timeUnit) {
    return WATCH.get().elapsed(timeUnit);
  }

  public StopwatchInternal stop() {
    StopwatchInternal internal = WATCH.get();
    WATCH.remove();
    return internal;
  }

  @Override
  public void close() throws Exception {
    WATCH.remove();
  }


  public static class StopwatchInternal {
    private final long start;

    StopwatchInternal(long start) {
      this.start = start;
    }

    long elapsedNanos() {
      if (start < 0) {
        throw new IllegalStateException("Stopwatch has not been started");
      }
      return System.nanoTime() - start;
    }

    public long elapsed(TimeUnit timeUnit) {
      return timeUnit.convert(elapsedNanos(), TimeUnit.NANOSECONDS);
    }
  }
}
