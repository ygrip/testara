package io.github.ygrip.testara.engine.executor;

import io.github.ygrip.testara.engine.model.WorkloadMetrics;
import lombok.extern.log4j.Log4j2;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects real-time execution metrics for adaptive parallelism decisions.
 * Tracks thread utilization, task completion, and system resources.
 */
@Log4j2
public class ExecutionMetricsCollector {

  private final ForkJoinPool pool;
  private final AtomicInteger completedTasks = new AtomicInteger(0);
  private final AtomicLong totalDurationMs = new AtomicLong(0);
  private final ConcurrentHashMap<Long, Long> taskStartTimes = new ConcurrentHashMap<>();

  public ExecutionMetricsCollector(ForkJoinPool pool) {
    this.pool = pool;
  }

  /**
   * Collect current workload metrics snapshot
   */
  public WorkloadMetrics collectMetrics() {
    int activeThreads = pool.getActiveThreadCount();
    int queuedTasks = (int) pool.getQueuedTaskCount();
    int completed = completedTasks.get();
    double avgDuration = calculateAvgDuration();
    double cpuUtil = getCpuUtilization();
    long availableMem = getAvailableMemory();
    int totalThreads = pool.getParallelism();

    WorkloadMetrics metrics = WorkloadMetrics.builder()
        .activeThreads(activeThreads)
        .queuedTasks(queuedTasks)
        .completedTasks(completed)
        .avgTaskDurationMs(avgDuration)
        .cpuUtilization(cpuUtil)
        .availableMemoryBytes(availableMem)
        .totalThreads(totalThreads)
        .build();

    log.trace("Collected metrics: {}", metrics);
    return metrics;
  }

  /**
   * Record when a task starts
   */
  public void recordTaskStart(long taskId) {
    taskStartTimes.put(taskId, System.currentTimeMillis());
  }

  /**
   * Record when a task completes
   */
  public void recordTaskComplete(long taskId) {
    Long startTime = taskStartTimes.remove(taskId);
    if (startTime != null) {
      long duration = System.currentTimeMillis() - startTime;
      totalDurationMs.addAndGet(duration);
      completedTasks.incrementAndGet();
    }
  }

  /**
   * Calculate average task duration in milliseconds
   */
  private double calculateAvgDuration() {
    int completed = completedTasks.get();
    if (completed == 0) {
      return 0.0;
    }
    return (double) totalDurationMs.get() / completed;
  }

  /**
   * Get current CPU utilization (0.0 to 1.0)
   */
  private double getCpuUtilization() {
    try {
      com.sun.management.OperatingSystemMXBean osBean =
          (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
      double cpuLoad = osBean.getCpuLoad();
      return cpuLoad >= 0 ? cpuLoad : 0.5; // Default to 50% if unavailable
    } catch (Exception e) {
      log.trace("Could not get CPU utilization", e);
      return 0.5; // Default
    }
  }

  /**
   * Get available memory in bytes
   */
  private long getAvailableMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.freeMemory();
  }

  /**
   * Reset metrics (useful for testing)
   */
  public void reset() {
    completedTasks.set(0);
    totalDurationMs.set(0);
    taskStartTimes.clear();
  }
}






