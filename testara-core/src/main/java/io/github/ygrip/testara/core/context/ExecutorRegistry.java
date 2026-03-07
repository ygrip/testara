package io.github.ygrip.testara.core.context;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central registry for all executors created during test execution.
 * Ensures all executors are properly shut down when tests complete.
 * 
 * This is the KEY to preventing orphaned threads - all executors are tracked
 * and automatically cleaned up when tests finish, regardless of how they end.
 * 
 * Thread-safe and can be called from multiple threads.
 */
@Log4j2
public class ExecutorRegistry {
  
  private static final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();
  private static final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
  private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
  private static volatile Thread shutdownHook;
  
  /**
   * Register an executor with the registry
   * 
   * @param name Unique name for the executor
   * @param executor Executor to register
   */
  public static void register(String name, ExecutorService executor) {
    if (shutdownInitiated.get()) {
      log.warn("Cannot register executor {} - shutdown already initiated", name);
      return;
    }
    
    executors.put(name, executor);
    log.debug("Registered executor: {} (total: {})", name, executors.size());
    
    // Register shutdown hook on first executor registration
    ensureShutdownHookRegistered();
  }
  
  /**
   * Unregister an executor (when it's shut down normally)
   * 
   * @param name Name of the executor
   */
  public static void unregister(String name) {
    ExecutorService removed = executors.remove(name);
    if (removed != null) {
      log.debug("Unregistered executor: {} (remaining: {})", name, executors.size());
    }
  }
  
  /**
   * Ensure shutdown hook is registered (thread-safe, only once)
   */
  private static void ensureShutdownHookRegistered() {
    if (shutdownHookRegistered.getAndSet(true)) {
      return;
    }
    
    shutdownHook = new Thread(() -> {
      if (!shutdownInitiated.get()) {
        log.warn("═══════════════════════════════════════════════════════════");
        log.warn("  JVM SHUTTING DOWN - ExecutorRegistry emergency cleanup");
        log.warn("  Active executors: {}", executors.size());
        log.warn("═══════════════════════════════════════════════════════════");
        shutdownAll(5, true);
      }
    }, "executor-registry-shutdown-hook");
    
    try {
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      log.debug("ExecutorRegistry shutdown hook registered");
    } catch (Exception e) {
      log.error("Failed to register shutdown hook", e);
    }
  }
  
  /**
   * Shutdown all registered executors
   * Called automatically when tests complete OR on JVM shutdown
   * 
   * @param timeoutSeconds Timeout for each executor
   * @param force If true, use shutdownNow for non-terminating executors
   */
  public static void shutdownAll(int timeoutSeconds, boolean force) {
    if (shutdownInitiated.getAndSet(true)) {
      log.debug("Shutdown already initiated, skipping");
      return;
    }
    
    if (executors.isEmpty()) {
      log.debug("No executors to shut down");
      return;
    }
    
    log.info("═══════════════════════════════════════════════════════════");
    log.info("  ExecutorRegistry - Shutting down all executors");
    log.info("  Total executors: {}", executors.size());
    log.info("═══════════════════════════════════════════════════════════");
    
    List<String> failedShutdowns = new ArrayList<>();
    
    // Shutdown all executors
    executors.forEach((name, executor) -> {
      try {
        log.debug("Shutting down executor: {}", name);
        
        executor.shutdown();
        
        if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
          log.warn("Executor {} did not terminate within {}s", name, timeoutSeconds);
          
          if (force) {
            log.warn("Forcing shutdown of executor: {}", name);
            executor.shutdownNow();
            
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
              log.error("Executor {} did not terminate even after forced shutdown", name);
              failedShutdowns.add(name);
            }
          } else {
            failedShutdowns.add(name);
          }
        } else {
          log.debug("Executor {} shut down successfully", name);
        }
        
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Interrupted while shutting down executor: {}", name, e);
        failedShutdowns.add(name);
      } catch (Exception e) {
        log.error("Error shutting down executor: {}", name, e);
        failedShutdowns.add(name);
      }
    });
    
    executors.clear();
    
    if (failedShutdowns.isEmpty()) {
      log.info("✓ All executors shut down successfully");
    } else {
      log.error("✗ Failed to shut down executors: {}", failedShutdowns);
    }
    
    log.info("═══════════════════════════════════════════════════════════");
  }
  
  /**
   * Shutdown all registered executors (with default settings)
   * Automatically called when tests complete
   */
  public static void shutdownAll() {
    shutdownAll(5, true);
    
    // Remove shutdown hook since we're doing normal cleanup
    removeShutdownHook();
  }
  
  /**
   * Remove shutdown hook during normal shutdown
   */
  private static void removeShutdownHook() {
    try {
      if (shutdownHook != null && shutdownHookRegistered.get()) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        log.debug("ExecutorRegistry shutdown hook removed");
      }
    } catch (IllegalStateException e) {
      // JVM already shutting down, ignore
      log.trace("Cannot remove shutdown hook - JVM already shutting down");
    } catch (Exception e) {
      log.warn("Error removing shutdown hook", e);
    }
  }
  
  /**
   * Get count of registered executors
   */
  public static int getExecutorCount() {
    return executors.size();
  }
  
  /**
   * Get names of all registered executors
   */
  public static List<String> getExecutorNames() {
    return new ArrayList<>(executors.keySet());
  }
  
  /**
   * Check if any executors are registered
   */
  public static boolean hasExecutors() {
    return !executors.isEmpty();
  }
  
  /**
   * Reset the registry (for testing)
   */
  public static void reset() {
    shutdownAll(2, true);
    shutdownInitiated.set(false);
    shutdownHookRegistered.set(false);
  }
  
  /**
   * Log current status
   */
  public static void logStatus() {
    log.info("ExecutorRegistry Status:");
    log.info("  Total executors: {}", executors.size());
    log.info("  Shutdown initiated: {}", shutdownInitiated.get());
    if (!executors.isEmpty()) {
      log.info("  Registered executors: {}", String.join(", ", executors.keySet()));
    }
  }
}




