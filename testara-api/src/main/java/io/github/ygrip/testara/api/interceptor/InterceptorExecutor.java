package io.github.ygrip.testara.api.interceptor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class InterceptorExecutor {

  private static final ExecutorService EXECUTOR =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("api-interceptor-", 0).factory());

  static {
    // Prevent JVM hanging on shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      EXECUTOR.shutdown();
      try {
        if (!EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
          EXECUTOR.shutdownNow();
        }
      } catch (InterruptedException ignored) {
        EXECUTOR.shutdownNow();
      }
    }, "api-interceptor-shutdown"));
  }

  private InterceptorExecutor() {
  }

  public static ExecutorService executor() {
    return EXECUTOR;
  }
}
