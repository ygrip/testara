package io.github.ygrip.testara.ui.driver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.ygrip.testara.core.context.ResourceShutdownRegistry;
import lombok.extern.log4j.Log4j2;

/**
 * Thread-local holder for the {@link DriverInstances} bound to the current worker thread.
 * <p>
 * A worker thread's drivers persist across every scenario it executes during a run - they are
 * only quit when the whole run ends. Tracks every {@link DriverInstances} ever created here so
 * {@link #shutdownAll()} can close sessions across all worker threads at that point, mirroring
 * {@link io.github.ygrip.testara.ui.proxy.ProxyInstanceManager}.
 */
@Log4j2
public final class DriverSessionManager {
  private static final ThreadLocal<DriverInstances> DRIVER_INSTANCES_THREAD_LOCAL = new ThreadLocal<>();

  private static final Set<DriverInstances> ALL_INSTANCES = ConcurrentHashMap.newKeySet();
  private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);
  private static final AtomicBoolean SHUTDOWN_INITIATED = new AtomicBoolean(false);

  private DriverSessionManager(){

  }

  public static DriverInstances inThisTestThread() {
    DriverInstances instances = DRIVER_INSTANCES_THREAD_LOCAL.get();
    if (instances == null) {
      instances = new DriverInstances();
      DRIVER_INSTANCES_THREAD_LOCAL.set(instances);
      ALL_INSTANCES.add(instances);
      ensureShutdownHookRegistered();
    }

    return instances;
  }

  public static DriverInstances getInstances() {
    return DRIVER_INSTANCES_THREAD_LOCAL.get();
  }

  public static void bindToCurrentThread(DriverInstances instances) {
    if (instances == null) {
      DRIVER_INSTANCES_THREAD_LOCAL.remove();
      return;
    }
    DRIVER_INSTANCES_THREAD_LOCAL.set(instances);
  }

  /**
   * Tear down the current thread's driver sessions right now. For explicit/single-test use;
   * scenario hooks no longer call this - see {@link #shutdownAll()}.
   */
  public static void tearDown() {
    DriverInstances instances = DRIVER_INSTANCES_THREAD_LOCAL.get();
    if (instances == null) {
      return;
    }
    instances.clearCurrentActiveDriver();
    instances.closeAllDrivers();
    ALL_INSTANCES.remove(instances);
    DRIVER_INSTANCES_THREAD_LOCAL.remove();
  }

  /**
   * Destroy every driver session still tracked, across every worker thread that created one.
   * Intended to run once, at the end of the whole test run (registered with
   * {@link ResourceShutdownRegistry}). Idempotent - safe to call more than once.
   */
  public static void shutdownAll() {
    if (SHUTDOWN_INITIATED.getAndSet(true)) {
      return;
    }
    if (ALL_INSTANCES.isEmpty()) {
      log.debug("No driver instances to shut down");
      return;
    }

    log.info("Shutting down all remaining driver instances ({} tracked)", ALL_INSTANCES.size());
    for (DriverInstances instances : ALL_INSTANCES) {
      try {
        instances.closeAllDrivers();
      } catch (Exception e) {
        log.warn("Error closing driver instances during shutdownAll: {}", e.getMessage());
      }
    }
    ALL_INSTANCES.clear();
    log.debug("All driver instances shut down");
  }

  /**
   * Reset the shutdown flag so that {@link #shutdownAll()} can run again.
   * Primarily useful for test harnesses that re-use the JVM.
   */
  static void resetShutdownState() {
    SHUTDOWN_INITIATED.set(false);
  }

  private static void ensureShutdownHookRegistered() {
    if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
      ResourceShutdownRegistry.register("driver-sessions", DriverSessionManager::shutdownAll);
      log.debug("Driver session shutdown callback registered");
    }
  }
}
