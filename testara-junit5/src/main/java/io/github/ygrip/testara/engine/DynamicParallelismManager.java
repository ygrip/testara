package io.github.ygrip.testara.engine;

import io.github.ygrip.testara.engine.executor.MemoryPressureMonitor;
import io.github.ygrip.testara.engine.model.ScalingRecommendation;
import io.github.ygrip.testara.engine.model.WorkloadMetrics;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.log4j.Log4j2;

import java.lang.management.ManagementFactory;

/**
 * Manages dynamic parallelism based on system resources and workload characteristics.
 *
 * Key features:
 * - Auto-detects optimal parallelism based on CPU cores and memory
 * - Adjusts for I/O-bound vs CPU-bound workloads
 * - Respects configured min/max bounds
 */
@Log4j2
public final class DynamicParallelismManager {

  private final TestaraCucumberEngineOptions options;
  private final int minParallelism;
  private final int maxParallelism;
  private final int availableCores;
  private final long totalMemory;
  private final boolean autoDetect;

  // Scaling thresholds
  private final double scaleUpThreshold;
  private final double scaleDownThreshold;

  // Memory per thread estimate (default 256 MB)
  private static final long DEFAULT_MEMORY_PER_THREAD = 256 * 1024 * 1024;

  public DynamicParallelismManager(TestaraCucumberEngineOptions options) {
    this.options = options;

    // Load configuration
    this.minParallelism = options.getConfigurationParameters()
        .get("cucumber.execution.parallel.config.dynamic.min", Integer::parseInt)
        .orElse(1);

    this.availableCores = Runtime.getRuntime().availableProcessors();

    this.maxParallelism = options.getConfigurationParameters()
        .get("cucumber.execution.parallel.config.dynamic.max", Integer::parseInt)
        .orElse(Math.max(8, availableCores * 2));

    this.totalMemory = Runtime.getRuntime().maxMemory();

    this.autoDetect = options.getConfigurationParameters()
        .getBoolean("cucumber.execution.parallel.config.dynamic.auto-detect")
        .orElse(true);

    this.scaleUpThreshold = options.getConfigurationParameters()
        .get("cucumber.execution.parallel.config.dynamic.scale-up-threshold", Double::parseDouble)
        .orElse(0.8);

    this.scaleDownThreshold = options.getConfigurationParameters()
        .get("cucumber.execution.parallel.config.dynamic.scale-down-threshold", Double::parseDouble)
        .orElse(0.3);

    log.trace("DynamicParallelismManager initialized: min={}, max={}, cores={}, autoDetect={}",
        minParallelism, maxParallelism, availableCores, autoDetect);
  }

  /**
   * Calculate optimal initial parallelism based on system resources
   */
  public int calculateInitialParallelism() {
    if (!autoDetect) {
      // Use configured initial value
      int initial = options.getConfigurationParameters()
          .get("cucumber.execution.parallel.config.dynamic.initial", Integer::parseInt)
          .orElse(minParallelism);
      log.debug("Using configured initial parallelism: {}", initial);
      return clamp(initial);
    }

    // Start with CPU cores as baseline
    int baseParallelism = availableCores;

    // Get workload type hint if provided
    String workloadType = options.getConfigurationParameters()
        .get("cucumber.execution.parallel.config.dynamic.workload-type")
        .orElse("auto");

    // Adjust based on workload type
    switch (workloadType.toLowerCase()) {
      case "io-bound":
        // I/O-bound tests can use more threads (less CPU intensive)
        baseParallelism = (int) Math.ceil(availableCores * 1.5);
        log.debug("I/O-bound workload hint: increasing parallelism to {}", baseParallelism);
        break;

      case "cpu-bound":
        // CPU-bound tests should use fewer threads
        baseParallelism = Math.max(1, availableCores - 1);
        log.debug("CPU-bound workload hint: reducing parallelism to {}", baseParallelism);
        break;

      case "mixed":
      case "auto":
      default:
        // Use CPU cores as-is
        log.debug("Mixed/auto workload: using CPU cores as parallelism: {}", baseParallelism);
        break;
    }

    // Cap based on memory constraints
    long memoryPerThread = DEFAULT_MEMORY_PER_THREAD;
    int maxThreadsByMemory = (int) (totalMemory / memoryPerThread);

    if (baseParallelism > maxThreadsByMemory) {
      log.warn("Memory constraint limits parallelism from {} to {} (total memory: {} MB)",
          baseParallelism, maxThreadsByMemory, totalMemory / (1024 * 1024));
      baseParallelism = maxThreadsByMemory;
    }

    int initial = clamp(baseParallelism);
    log.debug("Calculated initial parallelism: {} (cores={}, memory={} MB)",
        initial, availableCores, totalMemory / (1024 * 1024));

    return initial;
  }

  /**
   * Analyze workload and recommend scaling action
   * @deprecated Use analyzeWorkload(WorkloadMetrics, MemoryPressureLevel) instead
   */
  @Deprecated
  public ScalingRecommendation analyzeWorkload(WorkloadMetrics metrics) {
    return analyzeWorkload(metrics, MemoryPressureMonitor.MemoryPressureLevel.LOW);
  }

  /**
   * Analyze workload and recommend scaling action with memory awareness
   */
  public ScalingRecommendation analyzeWorkload(WorkloadMetrics metrics, 
      MemoryPressureMonitor.MemoryPressureLevel memoryPressure) {
    double utilization = metrics.getThreadUtilization();
    double loadFactor = metrics.getLoadFactor();

    log.debug("Analyzing workload: {} | Memory: {}", metrics, memoryPressure);

    // CRITICAL memory pressure - force scale down immediately
    if (memoryPressure == MemoryPressureMonitor.MemoryPressureLevel.CRITICAL) {
      log.warn("CRITICAL memory pressure - forcing SCALE_DOWN");
      return ScalingRecommendation.SCALE_DOWN;
    }

    // HIGH memory pressure - scale down if utilization allows
    if (memoryPressure == MemoryPressureMonitor.MemoryPressureLevel.HIGH &&
        utilization < 0.9) {
      log.debug("HIGH memory pressure with low utilization - recommending SCALE_DOWN");
      return ScalingRecommendation.SCALE_DOWN;
    }

    // MODERATE or HIGH memory pressure - block scale up
    if (memoryPressure.ordinal() >= MemoryPressureMonitor.MemoryPressureLevel.MODERATE.ordinal()) {
      if (utilization >= scaleUpThreshold && metrics.getQueuedTasks() > 5) {
        log.debug("Would SCALE_UP but blocked by memory pressure: {}", memoryPressure);
        return ScalingRecommendation.MAINTAIN;
      }
    }

    // Scale up if:
    // - High thread utilization AND significant queue backlog
    // - OR very high load factor (many queued tasks per thread)
    // - AND memory allows
    if ((utilization >= scaleUpThreshold && metrics.getQueuedTasks() > 5) ||
        loadFactor > 2.0) {
      log.debug("Recommendation: SCALE_UP (utilization={}%, queue={}, loadFactor={})",
          utilization * 100, metrics.getQueuedTasks(), loadFactor);
      return ScalingRecommendation.SCALE_UP;
    }

    // Scale down if:
    // - Low thread utilization AND no queued tasks
    // - AND not in a long-running test scenario
    if (utilization < scaleDownThreshold &&
        metrics.getQueuedTasks() == 0 &&
        metrics.getCompletedTasks() > 10) { // Have done some work already
      log.debug("Recommendation: SCALE_DOWN (utilization={}%, queue={})",
          utilization * 100, metrics.getQueuedTasks());
      return ScalingRecommendation.SCALE_DOWN;
    }

    log.trace("Recommendation: MAINTAIN (utilization={}%, queue={})",
        utilization * 100, metrics.getQueuedTasks());
    return ScalingRecommendation.MAINTAIN;
  }

  /**
   * Calculate new target parallelism based on current state and recommendation
   */
  public int calculateTargetParallelism(int currentParallelism, ScalingRecommendation recommendation,
      WorkloadMetrics metrics) {

    if (recommendation == ScalingRecommendation.MAINTAIN) {
      return currentParallelism;
    }

    int targetParallelism = currentParallelism;

    if (recommendation == ScalingRecommendation.SCALE_UP) {
      // Increase by 25-50% based on load factor
      double loadFactor = metrics.getLoadFactor();
      double increaseFactor = 1.25 + Math.min(loadFactor * 0.1, 0.25);
      targetParallelism = (int) Math.ceil(currentParallelism * increaseFactor);

      log.debug("Scaling up from {} to {} (factor: {})",
          currentParallelism, targetParallelism, increaseFactor);
    } else if (recommendation == ScalingRecommendation.SCALE_DOWN) {
      // Decrease by 25%
      targetParallelism = (int) Math.ceil(currentParallelism * 0.75);

      log.debug("Scaling down from {} to {}", currentParallelism, targetParallelism);
    }

    return clamp(targetParallelism);
  }

  /**
   * Calculate gradual adjustment from current to target parallelism
   * Don't change by more than 25% at once for stability
   */
  public int calculateGradualAdjustment(int currentParallelism, int targetParallelism) {
    if (currentParallelism == targetParallelism) {
      return currentParallelism;
    }

    // Calculate max change (25% of current)
    int maxChange = Math.max(1, (int) Math.ceil(currentParallelism * 0.25));

    int newParallelism;
    if (targetParallelism > currentParallelism) {
      // Scaling up
      newParallelism = Math.min(currentParallelism + maxChange, targetParallelism);
    } else {
      // Scaling down
      newParallelism = Math.max(currentParallelism - maxChange, targetParallelism);
    }

    newParallelism = clamp(newParallelism);

    if (newParallelism != currentParallelism) {
      log.debug("Gradual adjustment: {} -> {} (target: {}, maxChange: {})",
          currentParallelism, newParallelism, targetParallelism, maxChange);
    }

    return newParallelism;
  }

  /**
   * Get current CPU utilization (0.0 to 1.0)
   */
  public double getCPUUtilization() {
    try {
      OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
      // Use getCpuLoad() instead of deprecated getSystemCpuLoad()
      double cpuLoad = osBean.getCpuLoad();
      return cpuLoad >= 0 ? cpuLoad : 0.5; // Default to 50% if unavailable
    } catch (Exception e) {
      log.debug("Could not get CPU utilization", e);
      return 0.5; // Default
    }
  }

  /**
   * Get available memory in bytes
   */
  public long getAvailableMemory() {
    return Runtime.getRuntime().freeMemory();
  }

  /**
   * Clamp parallelism to configured bounds
   */
  private int clamp(int parallelism) {
    return Math.max(minParallelism, Math.min(maxParallelism, parallelism));
  }

  public int getMinParallelism() {
    return minParallelism;
  }

  public int getMaxParallelism() {
    return maxParallelism;
  }

  public int getAvailableCores() {
    return availableCores;
  }
}

