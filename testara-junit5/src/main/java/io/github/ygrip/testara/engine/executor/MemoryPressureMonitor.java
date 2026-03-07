package io.github.ygrip.testara.engine.executor;

import lombok.extern.log4j.Log4j2;

/**
 * Monitors memory pressure to prevent OOM errors and guide scaling decisions.
 * 
 * Pressure levels:
 * - LOW (0-70%): Safe to scale up
 * - MODERATE (70-85%): Maintain current parallelism
 * - HIGH (85-95%): Consider scaling down
 * - CRITICAL (>95%): Force immediate scale down
 */
@Log4j2
public class MemoryPressureMonitor {

  private final double highPressureThreshold;
  private final double criticalPressureThreshold;

  // Default thresholds
  private static final double DEFAULT_HIGH_THRESHOLD = 0.85;
  private static final double DEFAULT_CRITICAL_THRESHOLD = 0.95;
  private static final double MODERATE_THRESHOLD = 0.70;

  /**
   * Memory pressure levels
   */
  public enum MemoryPressureLevel {
    LOW,      // < 70% memory used - safe to scale up
    MODERATE, // 70-85% memory used - maintain current
    HIGH,     // 85-95% memory used - consider scaling down
    CRITICAL  // > 95% memory used - force scale down
  }

  public MemoryPressureMonitor() {
    this(DEFAULT_HIGH_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD);
  }

  public MemoryPressureMonitor(double highThreshold, double criticalThreshold) {
    this.highPressureThreshold = highThreshold;
    this.criticalPressureThreshold = criticalThreshold;
    
    log.trace("MemoryPressureMonitor initialized: high={}, critical={}",
        highThreshold, criticalThreshold);
  }

  /**
   * Get current memory pressure level
   */
  public MemoryPressureLevel getCurrentPressure() {
    double usageRatio = getMemoryUsageRatio();

    if (usageRatio >= criticalPressureThreshold) {
      log.warn("CRITICAL memory pressure: {}%", usageRatio * 100);
      return MemoryPressureLevel.CRITICAL;
    } else if (usageRatio >= highPressureThreshold) {
      log.debug("HIGH memory pressure: {}%", usageRatio * 100);
      return MemoryPressureLevel.HIGH;
    } else if (usageRatio >= MODERATE_THRESHOLD) {
      log.trace("MODERATE memory pressure: {}%", usageRatio * 100);
      return MemoryPressureLevel.MODERATE;
    } else {
      log.trace("LOW memory pressure: {}%", usageRatio * 100);
      return MemoryPressureLevel.LOW;
    }
  }

  /**
   * Get memory usage ratio (0.0 to 1.0)
   */
  public double getMemoryUsageRatio() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    
    long usedMemory = totalMemory - freeMemory;
    return (double) usedMemory / maxMemory;
  }

  /**
   * Check if we should prevent scaling up due to memory constraints
   */
  public boolean shouldPreventScaleUp() {
    MemoryPressureLevel pressure = getCurrentPressure();
    return pressure.ordinal() >= MemoryPressureLevel.MODERATE.ordinal();
  }

  /**
   * Check if we should force scaling down due to critical memory
   */
  public boolean shouldForceScaleDown() {
    return getCurrentPressure() == MemoryPressureLevel.CRITICAL;
  }

  /**
   * Suggest garbage collection if memory pressure is critical
   * This is a hint to the JVM, not a guarantee
   */
  public void suggestGarbageCollection() {
    if (getCurrentPressure() == MemoryPressureLevel.CRITICAL) {
      log.warn("Critical memory pressure - suggesting garbage collection");
      System.gc();
    }
  }

  /**
   * Get formatted memory status for logging
   */
  public String getMemoryStatus() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    
    return String.format("Memory: %dMB used / %dMB max (%.1f%%) - Pressure: %s",
        usedMemory / (1024 * 1024),
        maxMemory / (1024 * 1024),
        getMemoryUsageRatio() * 100,
        getCurrentPressure());
  }
}






