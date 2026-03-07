package io.github.ygrip.testara.engine.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Metrics about current workload for dynamic parallelism decisions
 */
@Getter
@Builder
public class WorkloadMetrics {
  private final int activeThreads;
  private final int queuedTasks;
  private final int completedTasks;
  private final double avgTaskDurationMs;
  private final double cpuUtilization;
  private final long availableMemoryBytes;
  private final int totalThreads;

  /**
   * Calculate thread utilization percentage (0.0 to 1.0)
   */
  public double getThreadUtilization() {
    if (totalThreads == 0) {
      return 0.0;
    }
    return (double) activeThreads / totalThreads;
  }

  /**
   * Determine if the workload is I/O-bound
   * Heuristic: Low CPU utilization despite high thread utilization
   */
  public boolean isIOBound() {
    return getThreadUtilization() > 0.7 && cpuUtilization < 0.5;
  }

  /**
   * Determine if the workload is CPU-bound
   * Heuristic: High CPU utilization
   */
  public boolean isCPUBound() {
    return cpuUtilization > 0.8;
  }

  /**
   * Get the load factor (queued tasks per thread)
   */
  public double getLoadFactor() {
    if (totalThreads == 0) {
      return 0.0;
    }
    return (double) queuedTasks / totalThreads;
  }

  @Override
  public String toString() {
    return String.format(
        "WorkloadMetrics{threads=%d/%d (%.1f%%), queue=%d, completed=%d, avgDuration=%.1fms, cpu=%.1f%%, loadFactor=%.2f}",
        activeThreads, totalThreads, getThreadUtilization() * 100,
        queuedTasks, completedTasks, avgTaskDurationMs,
        cpuUtilization * 100, getLoadFactor());
  }
}








