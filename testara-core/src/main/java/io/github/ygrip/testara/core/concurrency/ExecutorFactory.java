package io.github.ygrip.testara.core.concurrency;

import io.github.ygrip.testara.core.context.ExecutorRegistry;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Wrapper for ExecutorService that ensures proper cleanup and prevents orphaned threads.
 * Features:
 * - All threads are daemon threads (won't prevent JVM shutdown)
 * - Automatic timeout handling
 * - Guaranteed cleanup on close
 * - Exception handling and logging
 */
@Log4j2
public class ExecutorFactory {

  private static final ConcurrentMap<String, AtomicInteger> THREAD_COUNTERS = new ConcurrentHashMap<>();

  private static Thread newThread(Runnable r, String prefix, boolean daemon) {
    // Each prefix gets its own atomic counter
    AtomicInteger counter = THREAD_COUNTERS.computeIfAbsent(prefix, k -> new AtomicInteger());

    Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
    t.setDaemon(daemon);
    t.setUncaughtExceptionHandler((thread, ex) -> log.error("Uncaught in {}: {}",
        thread.getName(),
        ex.getMessage(),
        ex));
    return t;
  }

  /**
   * Create a safe cached thread pool with daemon threads
   * Automatically registers with ExecutorRegistry for cleanup
   *
   * @param threadNamePrefix Prefix for thread names
   * @return ExecutorService with daemon threads
   */
  public static ExecutorService createSafeCachedThreadPool(String threadNamePrefix) {
    ExecutorService executor = new ThreadPoolExecutor(0,
        Integer.max(2, Runtime.getRuntime().availableProcessors() * 2),
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        r -> newThread(r, threadNamePrefix, true),
        (r, e) -> log.warn("Task rejected in {}: {}", threadNamePrefix, r));

    // Auto-register for cleanup
    ExecutorRegistry.register(threadNamePrefix, executor);

    return executor;
  }

  /**
   * Create a safe fixed thread pool with daemon threads
   * Automatically registers with ExecutorRegistry for cleanup
   *
   * @param nThreads         Number of threads
   * @param threadNamePrefix Prefix for thread names
   * @return ExecutorService with daemon threads
   */
  public static ExecutorService createSafeFixedThreadPool(int nThreads, String threadNamePrefix) {
    ExecutorService executor = Executors.newFixedThreadPool(nThreads, r -> newThread(r, threadNamePrefix, true));

    // Auto-register for cleanup
    ExecutorRegistry.register(threadNamePrefix, executor);

    return executor;
  }

  /**
   * Create a safe scheduled thread pool with daemon threads
   * Automatically registers with ExecutorRegistry for cleanup
   *
   * @param corePoolSize     Core pool size
   * @param threadNamePrefix Prefix for thread names
   * @return ScheduledExecutorService with daemon threads
   */
  public static ScheduledExecutorService createSafeScheduledThreadPool(int corePoolSize, String threadNamePrefix) {
    ScheduledExecutorService executor =
        Executors.newScheduledThreadPool(corePoolSize, r -> newThread(r, threadNamePrefix, true));

    // Auto-register for cleanup
    ExecutorRegistry.register(threadNamePrefix, executor);

    return executor;
  }

  /**
   * Safely shutdown an executor service with timeout
   * Automatically unregisters from ExecutorRegistry
   *
   * @param executor       Executor to shutdown
   * @param timeoutSeconds Timeout in seconds
   * @param executorName   Name for logging
   * @return true if shutdown completed within timeout
   */
  public static boolean safeShutdown(ExecutorService executor, int timeoutSeconds, String executorName) {
    if (executor == null || executor.isShutdown()) {
      ExecutorRegistry.unregister(executorName);
      return true;
    }

    log.trace("Shutting down executor: {}", executorName);

    // Initiate shutdown
    executor.shutdown();

    try {
      // Wait for termination
      if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        log.warn("Executor {} did not terminate within {} seconds, forcing shutdown", executorName, timeoutSeconds);

        // Force shutdown
        executor.shutdownNow();

        // Wait a bit more
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
          log.error("Executor {} did not terminate even after forced shutdown", executorName);
          ExecutorRegistry.unregister(executorName);
          return false;
        }
      }

      log.trace("Executor {} shut down successfully", executorName);
      ExecutorRegistry.unregister(executorName);
      return true;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting for executor {} termination", executorName);
      executor.shutdownNow();
      ExecutorRegistry.unregister(executorName);
      return false;
    } finally {
      ExecutorRegistry.unregister(executorName);
    }
  }

  /**
   * Execute a task with timeout
   *
   * @param executor       Executor to use
   * @param task           Task to execute
   * @param timeoutSeconds Timeout in seconds
   * @param taskName       Task name for logging
   * @return Future result
   * @throws TimeoutException if task times out
   */
  public static <T> T executeWithTimeout(ExecutorService executor,
      Callable<T> task,
      int timeoutSeconds,
      String taskName) throws TimeoutException, ExecutionException, InterruptedException {

    log.trace("Executing task with timeout: {} ({}s)", taskName, timeoutSeconds);

    Future<T> future = executor.submit(task);

    try {
      T result = future.get(timeoutSeconds, TimeUnit.SECONDS);
      log.trace("Task completed: {}", taskName);
      return result;

    } catch (TimeoutException e) {
      log.error("Task timed out after {}s: {}", timeoutSeconds, taskName);
      future.cancel(true);
      // Ensure no thread leak
      executor.submit(() -> Thread.currentThread().interrupt());
      throw e;

    } catch (ExecutionException e) {
      log.error("Task failed: {}", taskName, e.getCause());
      throw e;

    } catch (InterruptedException e) {
      log.warn("Task interrupted: {}", taskName);
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  /**
   * Run a task asynchronously with proper error handling
   *
   * @param executor Executor to use
   * @param task     Task to run
   * @param taskName Task name for logging
   * @return CompletableFuture
   */
  public static CompletableFuture<Void> runAsync(ExecutorService executor, Runnable task, String taskName) {

    return CompletableFuture.runAsync(() -> {
      try {
        log.trace("Starting async task: {}", taskName);
        task.run();
        log.trace("Completed async task: {}", taskName);
      } catch (Exception e) {
        log.error("Async task failed: {}", taskName, e);
        throw new CompletionException(e);
      }
    }, executor);
  }

  /**
   * Create a ForkJoinPool with proper configuration
   *
   * @param parallelism      Parallelism level
   * @param threadNamePrefix Thread name prefix
   * @return Configured ForkJoinPool with daemon threads
   */
  public static ForkJoinPool createSafeForkJoinPool(int parallelism, String threadNamePrefix) {
    return new ForkJoinPool(parallelism, pool -> {
      ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
      worker.setName(threadNamePrefix + "-" + worker.getPoolIndex());
      worker.setDaemon(false);
      return worker;
    }, (thread, exception) -> {
      log.error("Uncaught exception in ForkJoinPool thread {}: {}",
          thread.getName(),
          exception.getMessage(),
          exception);
    }, true  // asyncMode for better work distribution
    );
  }

  /**
   * Create an unbounded virtual thread executor per task (Java 21+ Project Loom)
   * Each task gets its own virtual thread - suitable for high-throughput scenarios
   *
   * @param threadNamePrefix Prefix for thread names
   * @return ExecutorService that creates a virtual thread per task
   */
  public static ExecutorService createVirtualThreadPerTaskExecutor(String threadNamePrefix) {
    // Java 21's Executors.newVirtualThreadPerTaskExecutor() equivalent with naming
    ExecutorService executor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(threadNamePrefix + "-", 0).factory());

    // Auto-register for cleanup
    ExecutorRegistry.register(threadNamePrefix, executor);

    log.debug("Created virtual thread per-task executor: {}", threadNamePrefix);
    return executor;
  }

  /**
   * Create a bounded platform thread pool with customizable min/max threads
   * Uses ThreadPoolExecutor with configurable bounds
   *
   * @param minThreads       Core pool size
   * @param maxThreads       Maximum pool size
   * @param threadNamePrefix Prefix for thread names
   * @return ExecutorService with bounded platform threads
   */
  public static ExecutorService createBoundedPlatformThreadPool(int minThreads,
      int maxThreads,
      String threadNamePrefix) {

    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
          Thread t = new Thread(r, threadNamePrefix + "-" + System.currentTimeMillis());
          t.setDaemon(true);  // Critical: won't prevent JVM shutdown
          t.setUncaughtExceptionHandler((thread, exception) -> {
            log.error("Uncaught exception in thread {}: {}", thread.getName(), exception.getMessage(), exception);
          });
          return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy()  // Back-pressure policy
        );

    // Auto-register for cleanup
    ExecutorRegistry.register(threadNamePrefix, executor);

    log.debug("Created bounded platform thread pool: min={}, max={}, prefix={}",
        minThreads,
        maxThreads,
        threadNamePrefix);
    return executor;
  }

  /**
   * Wrap a task so it runs with the calling thread's propagated context bound on whichever
   * thread actually executes it - see {@link ThreadContextPropagator}. Without this, a task
   * dispatched onto a fresh virtual/worker thread (e.g. via {@link #createVirtualThreadPerTaskExecutor})
   * silently sees an empty/default context instead of the caller's (driver sessions, actors,
   * etc.), since {@code ThreadLocal} state does not cross to unrelated threads.
   *
   * @param task the task to run with propagated context
   * @return a supplier that captures the calling thread's context now and rebinds/unbinds it
   *     around {@code task} when invoked, wherever that ends up running
   */
  public static <T> Supplier<T> withPropagatedContext(Supplier<T> task) {
    List<ThreadContextPropagator> propagators = ThreadContextPropagatorLoader.load();
    if (propagators.isEmpty()) {
      return task;
    }
    List<Object> snapshots = propagators.stream()
        .map(ThreadContextPropagator::capture)
        .toList();

    return () -> {
      for (int i = 0; i < propagators.size(); i++) {
        propagators.get(i).bind(snapshots.get(i));
      }
      try {
        return task.get();
      } finally {
        propagators.forEach(ThreadContextPropagator::unbind);
      }
    };
  }

  /**
   * Creates a dynamically scaling virtual-thread executor
   * with bounded queue capacity and controlled parallelism.
   *
   * @param minThreads minimum concurrent workers
   * @param maxThreads maximum concurrent workers
   * @param prefix     thread name prefix
   * @return configured ExecutorService
   */
  public static ExecutorService createDynamicVirtualThreadExecutor(int minThreads, int maxThreads, String prefix) {
    int availableCores = Runtime.getRuntime().availableProcessors();

    // Default fallback if user passes invalid config
    if (minThreads <= 0)
      minThreads = Math.max(1, availableCores);
    if (maxThreads < minThreads)
      maxThreads = minThreads * 2;

    // Dynamically computed queue capacity
    // Rule of thumb: 2x max for general throughput, adjustable as needed.
    int queueCapacity = Math.max(maxThreads * 2, 64);

    BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueCapacity);
    ThreadFactory vtf = Thread.ofVirtual().name(prefix + "-", 0).factory();

    ThreadPoolExecutor executor = new ThreadPoolExecutor(minThreads,
        maxThreads,
        30L,
        TimeUnit.SECONDS,
        queue,
        vtf,
        new ThreadPoolExecutor.CallerRunsPolicy()
        // Apply backpressure if overloaded
    );

    executor.allowCoreThreadTimeOut(true);

    // Auto-register for cleanup
    ExecutorRegistry.register(prefix, executor);

    log.debug("Created bounded virtual thread pool: min={}, max={}, prefix={}", minThreads, maxThreads, prefix);

    return executor;
  }
}
