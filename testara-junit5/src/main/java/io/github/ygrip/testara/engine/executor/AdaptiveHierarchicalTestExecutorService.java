package io.github.ygrip.testara.engine.executor;

import io.github.ygrip.testara.core.concurrency.ExecutorFactory;
import io.github.ygrip.testara.engine.DynamicParallelismManager;
import io.github.ygrip.testara.engine.model.ScalingRecommendation;
import io.github.ygrip.testara.engine.model.WorkloadMetrics;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutorService;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adaptive test executor service that dynamically adjusts parallelism at runtime based on workload characteristics and memory pressure.
 * * - Monitors workload every N seconds
 * * - Adjusts parallelism based on thread utilization and memory pressure
 * * - Uses safe pool recreation when scaling is needed
 */
@Log4j2
public class AdaptiveHierarchicalTestExecutorService implements HierarchicalTestExecutorService {
  private final AtomicReference<ForkJoinPoolHierarchicalTestExecutorService> delegateService;
  private final AtomicInteger currentParallelism;
  private final DynamicParallelismManager parallelismManager;
  private final ExecutionMetricsCollector metricsCollector;
  private final MemoryPressureMonitor memoryMonitor;
  private final ScheduledExecutorService scalingScheduler;
  private final ExecutorService cleanupExecutor;
  private final ConfigurationParameters config;
  private final String configPrefix;
  private final int scalingIntervalSeconds;
  private volatile boolean closed = false;
  private volatile ForkJoinPool currentPool;

  /**
   * Create adaptive executor service with initial parallelism
   */
  public AdaptiveHierarchicalTestExecutorService(int initialParallelism,
      DynamicParallelismManager parallelismManager,
      ConfigurationParameters config,
      String configPrefix) {
    this.parallelismManager = parallelismManager;
    this.config = config;
    this.configPrefix = configPrefix;
    this.currentParallelism = new AtomicInteger(initialParallelism);

    double highThreshold = config.get("cucumber.execution.parallel.config.dynamic.memory.high-threshold", Double::parseDouble)
        .orElse(0.85);
    double criticalThreshold = config.get("cucumber.execution.parallel.config.dynamic.memory.critical-threshold", Double::parseDouble)
        .orElse(0.95);
    this.memoryMonitor = new MemoryPressureMonitor(highThreshold, criticalThreshold);

    this.scalingIntervalSeconds = config.get("cucumber.execution.parallel.config.dynamic.scaling-interval-seconds", Integer::parseInt)
        .orElse(10);

    this.currentPool = createForkJoinPool(initialParallelism);
    this.metricsCollector = new ExecutionMetricsCollector(currentPool);
    this.delegateService = new AtomicReference<>(createDelegateService(initialParallelism));

    // ✅ Non-daemon, single-thread scheduler (shuts down cleanly)
    this.scalingScheduler = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("adaptive-scaling-monitor-%d")
            .setDaemon(false)
            .build()
    );

    // ✅ Non-daemon cleanup pool
    this.cleanupExecutor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("testara-pool-cleanup-%d")
            .setDaemon(false)
            .build()
    );

    startScalingMonitor();
    log.debug("AdaptiveHierarchicalTestExecutorService initialized: parallelism={}, scalingInterval={}s",
        initialParallelism, scalingIntervalSeconds);
  }

  /**
   * Start the background scaling monitor
   */
  private void startScalingMonitor() {
    scalingScheduler.scheduleAtFixedRate(() -> {
      try {
        if (closed) {
          return;
        }
        performScalingCheck();
      } catch (Exception e) {
        log.error("Error in scaling monitor", e);
      }
    }, scalingIntervalSeconds, scalingIntervalSeconds, TimeUnit.SECONDS);
    log.debug("Scaling monitor started (interval: {}s)", scalingIntervalSeconds);
  }

  /**
   * Perform a scaling check and adjust if needed
   */
  private void performScalingCheck() {
    // Collect current metrics
    WorkloadMetrics metrics = metricsCollector.collectMetrics();
    MemoryPressureMonitor.MemoryPressureLevel memoryPressure = memoryMonitor.getCurrentPressure();
    log.debug("Scaling check: {} | {}", metrics, memoryMonitor.getMemoryStatus());
    // Get scaling recommendation
    ScalingRecommendation recommendation = parallelismManager.analyzeWorkload(metrics, memoryPressure);
    if (!recommendation.shouldScale()) {
      log.trace("No scaling needed");
    }
    // Calculate target parallelism
    int current = currentParallelism.get();
    int target = parallelismManager.calculateTargetParallelism(current, recommendation, metrics);
    int newParallelism = parallelismManager.calculateGradualAdjustment(current, target);
    if (newParallelism != current) {
      log.debug("Scaling: {} -> {} (recommendation: {}, target: {})", current, newParallelism, recommendation, target);
      adjustParallelism(newParallelism);
      // Suggest GC if memory is critical
      if (memoryPressure == MemoryPressureMonitor.MemoryPressureLevel.CRITICAL) {
        memoryMonitor.suggestGarbageCollection();
      }
    }
  }

  /**
   * Adjust parallelism by recreating the pool * This is necessary because ForkJoinPool parallelism cannot be changed after creation * (except in JDK 19+ with setParallelism, but we need JDK 8+ compatibility)
   */
  private synchronized void adjustParallelism(int newParallelism) {
    if (closed) return;

    ForkJoinPool oldPool = currentPool;
    ForkJoinPool newPool = createForkJoinPool(newParallelism);
    ForkJoinPoolHierarchicalTestExecutorService newService = createDelegateServiceWithPool(newPool);

    delegateService.set(newService);
    currentPool = newPool;
    currentParallelism.set(newParallelism);

    // ✅ Safe shutdown of old pool in background
    cleanupExecutor.submit(() -> {
      try {
        log.debug("Shutting down old pool ({} threads)", oldPool.getParallelism());
        oldPool.shutdown();
        if (!oldPool.awaitTermination(5, TimeUnit.SECONDS)) {
          log.warn("Old pool did not terminate within timeout, forcing shutdown");
          oldPool.shutdownNow();
        }
      } catch (Exception e) {
        log.error("Error shutting down old pool", e);
      }
    });
  }

  /**
   * Create a ForkJoinPool with specified parallelism
   */
  private ForkJoinPool createForkJoinPool(int parallelism) {
    return ExecutorFactory.createSafeForkJoinPool(parallelism, "testara-test-worker");
  }

  /**
   * Create delegate service with specified parallelism
   */
  private ForkJoinPoolHierarchicalTestExecutorService createDelegateService(int parallelism) {
    ConfigurationParameters dynamicConfig = createConfigWithParallelism(parallelism);
    return new ForkJoinPoolHierarchicalTestExecutorService(dynamicConfig);
  }

  /**
   * Create delegate service with an existing pool (for MVP - simplified version) * In full implementation, we'd use custom executor service that wraps the pool
   */
  private ForkJoinPoolHierarchicalTestExecutorService createDelegateServiceWithPool(ForkJoinPool pool) {
    // For MVP, we create a new service with matching parallelism
    // The pool will be used by JUnit's internal mechanisms
    return createDelegateService(pool.getParallelism());
  }

  /**
   * Create configuration with overridden parallelism
   */
  private ConfigurationParameters createConfigWithParallelism(int parallelism) {
    return new ConfigurationParameters() {
      @Override
      public Optional<String> get(String key) {
        if ("fixed.parallelism".equals(key)) {
          return Optional.of(String.valueOf(parallelism));
        }
        // Strip prefix and delegate to original config
        String fullKey = configPrefix + key;
        return config.get(fullKey);
      }

      @Override
      public Optional<Boolean> getBoolean(String key) {
        return get(key).map(Boolean::parseBoolean);
      }

      @Override
      @SuppressWarnings("deprecation")
      public int size() {
        return config.size();
      }

      @Override
      public java.util.Set<String> keySet() {
        return config.keySet();
      }
    };
  }

  // Delegate all HierarchicalTestExecutorService methods to current delegate
  @Override
  public Future<Void> submit(TestTask testTask) {
    // Record task start for metrics
    long taskId = System.identityHashCode(testTask);
    metricsCollector.recordTaskStart(taskId);
    Future<Void> future = delegateService.get().submit(testTask);
    // Wrap future to record completion
    return new FutureWrapper(future, taskId, metricsCollector);
  }

  @Override
  public void invokeAll(java.util.List<? extends TestTask> tasks) {
    // Record all task starts
    for (TestTask task : tasks) {
      long taskId = System.identityHashCode(task);
      metricsCollector.recordTaskStart(taskId);
    }
    delegateService.get().invokeAll(tasks);
    // Record all completions
    for (TestTask task : tasks) {
      long taskId = System.identityHashCode(task);
      metricsCollector.recordTaskComplete(taskId);
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;

    log.trace("Closing AdaptiveHierarchicalTestExecutorService...");

    scalingScheduler.shutdownNow();
    cleanupExecutor.shutdown();

    try {
      delegateService.get().close();
    } catch (Exception e) {
      log.warn("Error closing delegate service", e);
    }

    try {
      currentPool.shutdownNow();
      if (!currentPool.awaitTermination(3, TimeUnit.SECONDS)) {
        log.warn("Current pool did not terminate in time");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    log.trace("AdaptiveHierarchicalTestExecutorService closed cleanly");
  }

  /**
   * Get current parallelism level
   */
  public int getCurrentParallelism() {
    return currentParallelism.get();
  }

  /**
   * Future wrapper to track task completion
   */
  private static class FutureWrapper implements Future<Void> {
    private final Future<Void> delegate;
    private final long taskId;
    private final ExecutionMetricsCollector collector;

    FutureWrapper(Future<Void> delegate, long taskId, ExecutionMetricsCollector collector) {
      this.delegate = delegate;
      this.taskId = taskId;
      this.collector = collector;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      collector.recordTaskComplete(taskId);
      return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
      return delegate.isDone();
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
      try {
        return delegate.get();
      } finally {
        collector.recordTaskComplete(taskId);
      }
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      try {
        return delegate.get(timeout, unit);
      } finally {
        collector.recordTaskComplete(taskId);
      }
    }
  }
}