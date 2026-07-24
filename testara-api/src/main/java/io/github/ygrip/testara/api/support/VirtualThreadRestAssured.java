package io.github.ygrip.testara.api.support;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import lombok.extern.log4j.Log4j2;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Log4j2
public final class VirtualThreadRestAssured {

  // A global virtual-thread-per-task executor
  private static final ExecutorService VIRTUAL_EXECUTOR = createDynamicVirtualThreadExecutor("rest-assured");

  static {
    // Tear the shared executor down only on JVM exit, never from per-scenario cleanup.
    Runtime.getRuntime().addShutdownHook(new Thread(VirtualThreadRestAssured::shutdown,
        "rest-assured-executor-shutdown"));
  }

  private static ExecutorService createDynamicVirtualThreadExecutor(String prefix) {
    int availableCores = Runtime.getRuntime().availableProcessors();

    // Default fallback if user passes invalid config
    int minThreads = Math.max(1, availableCores);
    int maxThreads = minThreads * 2;

    // Dynamically computed queue capacity
    // Rule of thumb: 2x max for general throughput, adjustable as needed.
    int queueCapacity = Math.max(maxThreads * 2, 64);

    BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueCapacity);
    ThreadFactory vtf = Thread.ofVirtual().name(prefix + "-", 0).factory();

    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        minThreads,
        maxThreads,
        30L, TimeUnit.SECONDS,
        queue,
        vtf,
        new ThreadPoolExecutor.CallerRunsPolicy() // Apply backpressure if overloaded
    );

    executor.allowCoreThreadTimeOut(true);

    return executor;
  }

  private VirtualThreadRestAssured() {
  }

  static HttpClient createPoolingHttpClient() {
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    return HttpClientBuilder.create()
        .setConnectionManager(cm)
        .build();
  }

  /**
   * Globally enable RestAssured to run inside virtual threads.
   * Call this once in @BeforeAll of your base test class.
   */
  public static void enable() {
    RestAssured.config = RestAssuredConfig.config()
        .httpClient(HttpClientConfig.httpClientConfig()
            .httpClientFactory(VirtualThreadRestAssured::createPoolingHttpClient)
        );

    log.trace("[VirtualRestAssured] Enabled Apache HttpClient + Virtual Threads");
  }

  /**
   * Runs the given RestAssured call asynchronously inside a virtual thread.
   *
   * Example:
   * VirtualThreadRestAssured.run(() -> given().get("/api").then().statusCode(200));
   */
  static void run(Runnable task) {
    Future<?> f = VIRTUAL_EXECUTOR.submit(task);
    try {
      f.get(); // wait and propagate any exception
    } catch (ExecutionException e) {
      throwAsUnchecked(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Virtual thread interrupted", e);
    }
  }

  /**
   * For callables that return a result.
   */
  static <T> T call(Callable<T> task) {
    Future<T> f = VIRTUAL_EXECUTOR.submit(task);
    try {
      return f.get();
    } catch (ExecutionException e) {
      throwAsUnchecked(e.getCause());
      return null; // unreachable
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Virtual thread interrupted", e);
    }
  }

  private static void throwAsUnchecked(Throwable t) {
    if (t instanceof RuntimeException re) throw re;
    if (t instanceof Error e) throw e;
    throw new RuntimeException(t);
  }

  /**
   * Gracefully shuts down the executor when tests complete.
   */
  static void shutdown() {
    VIRTUAL_EXECUTOR.shutdownNow();
  }
}
