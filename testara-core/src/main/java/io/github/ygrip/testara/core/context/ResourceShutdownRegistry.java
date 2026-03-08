package io.github.ygrip.testara.core.context;

import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central callback registry for resource cleanup at the end of a test run.
 * <p>
 * Any module can register a named shutdown task via {@link #register(String, Runnable)}.
 * When the test run finishes, the engine calls {@link #shutdownAll()} to invoke every
 * registered callback exactly once.  A JVM shutdown hook acts as a safety net for
 * abnormal termination.
 * <p>
 * Thread-safe. Callbacks are invoked in no guaranteed order.
 */
@Log4j2
public final class ResourceShutdownRegistry {

  private static final Map<String, Runnable> CALLBACKS = new ConcurrentHashMap<>();
  private static final AtomicBoolean SHUTDOWN_INITIATED = new AtomicBoolean(false);
  private static final AtomicBoolean HOOK_REGISTERED = new AtomicBoolean(false);

  private ResourceShutdownRegistry() {
  }

  /**
   * Register a shutdown callback.  If a callback with the same name already exists
   * it is silently replaced.
   *
   * @param name     unique identifier (e.g. {@code "proxy-instances"})
   * @param callback action to execute during shutdown
   */
  public static void register(String name, Runnable callback) {
    CALLBACKS.put(name, callback);
    log.debug("Registered resource shutdown callback: {}", name);
    ensureShutdownHookRegistered();
  }

  /**
   * Remove a previously registered callback.
   */
  public static void unregister(String name) {
    CALLBACKS.remove(name);
  }

  /**
   * Execute all registered callbacks, then clear the registry.
   * Safe to call multiple times — only the first invocation runs the callbacks.
   */
  public static void shutdownAll() {
    if (SHUTDOWN_INITIATED.getAndSet(true)) {
      return;
    }
    if (CALLBACKS.isEmpty()) {
      log.debug("No resource shutdown callbacks registered");
      return;
    }

    log.info("Running {} resource shutdown callback(s)", CALLBACKS.size());
    CALLBACKS.forEach((name, callback) -> {
      try {
        log.debug("Executing shutdown callback: {}", name);
        callback.run();
      } catch (Exception e) {
        log.warn("Shutdown callback '{}' failed: {}", name, e.getMessage());
      }
    });
    CALLBACKS.clear();
    log.debug("All resource shutdown callbacks completed");
  }

  /**
   * Reset state so {@link #shutdownAll()} can fire again.
   * Intended for test harnesses that reuse the JVM.
   */
  public static void reset() {
    SHUTDOWN_INITIATED.set(false);
    CALLBACKS.clear();
  }

  private static void ensureShutdownHookRegistered() {
    if (HOOK_REGISTERED.compareAndSet(false, true)) {
      try {
        Runtime.getRuntime().addShutdownHook(
            Thread.ofPlatform().name("resource-shutdown-hook").unstarted(() -> {
              if (!SHUTDOWN_INITIATED.get()) {
                log.info("JVM shutting down — running resource shutdown callbacks");
                shutdownAll();
              }
            }));
        log.debug("Resource shutdown hook registered");
      } catch (Exception e) {
        log.error("Failed to register resource shutdown hook", e);
      }
    }
  }
}
