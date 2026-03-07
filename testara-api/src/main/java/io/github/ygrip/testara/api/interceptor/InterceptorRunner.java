package io.github.ygrip.testara.api.interceptor;

import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.concurrent.*;

@Log4j2
final class InterceptorRunner {

  private InterceptorRunner() {}

  static void run(Runnable task, Duration timeout) {
    try {
      Future<?> future =
          InterceptorExecutor.executor().submit(task);

      // Timeout applies ONLY to the interceptor thread
      future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // Cancel the virtual thread
      // (safe, cheap, no platform thread loss)
      log.warn("Interceptor execution timed out");
    } catch (Throwable t) {
      log.warn("Interceptor execution failed", t);
    }
  }
}

