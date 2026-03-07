package io.github.ygrip.testara.spring.scope;

import io.github.ygrip.testara.core.registry.RootRegistry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.NamedThreadLocal;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Spring Scope for automation testing that provides thread-isolated bean instances.
 * <p>
 * This scope integrates with testara-core's RootRegistry to ensure consistent scoping
 * across both Spring and non-Spring components.
 * <p>
 * Scope name: "testara-automation"
 * <p>
 * Usage in Spring:
 * {@code @Scope("testara-automation")}
 * <p>
 * Beans in this scope are:
 * - Thread-local (each thread gets its own instance)
 * - Automatically cleaned up on shutdown
 * - Integrated with testara-core's THREAD scope
 *
 * @author yunaz.ramadhan on 12/11/2021
 */
@Log4j2
public final class AutomationScope implements Scope {
  
  public static final String SCOPE_NAME = "testara-automation";
  
  private static final String CONVERSATION_ID = "testara-automation";
  private static final String THREAD_PREFIX = "automation-scope";
  
  // Singleton instance for sharing scope state
  private static volatile AutomationScope INSTANCE;
  
  // Thread-local storage for scoped objects
  private final ThreadLocal<Map<String, Object>> scopedObjects = new NamedThreadLocal<>(THREAD_PREFIX) {
    protected Map<String, Object> initialValue() {
      return new HashMap<>();
    }
  };
  // Thread-local storage for destruction callbacks
  private final ThreadLocal<Map<String, Runnable>> destructionCallbacks = new NamedThreadLocal<>(THREAD_PREFIX) {
    protected Map<String, Runnable> initialValue() {
      return new HashMap<>();
    }
  };
  /**
   * Global registry of destruction callbacks for shutdown
   */
  private final Map<String, Map<String, Runnable>> globalDestructionCallbacks = new ConcurrentHashMap<>();
  /**
   * Prevents double shutdown hook registration
   */
  private volatile boolean shutdownHookRegistered = false;

  public AutomationScope() {
    INSTANCE = this;
  }

  /**
   * Get the singleton instance of AutomationScope.
   * Useful for programmatic scope management.
   */
  public static AutomationScope getInstance() {
    return INSTANCE;
  }

  // ---------------------------------------------------------------------------
  // Core Scope Methods
  // ---------------------------------------------------------------------------

  @Override
  public Object get(String name, @NonNull ObjectFactory<?> objectFactory) {
    if (!scopedObjects.get().containsKey(name)) {
      scopedObjects.get().put(name, objectFactory.getObject());
    }
    return scopedObjects.get().get(name);
  }

  @Override
  public Object remove(String name) {
    destructionCallbacks.get().remove(name);
    return scopedObjects.get().remove(name);
  }

  @Override
  public void registerDestructionCallback(String name, @NonNull Runnable callback) {
    String threadId = getConversationId();
    // Store callbacks locally and globally
    destructionCallbacks.get().put(name, callback);
    globalDestructionCallbacks.computeIfAbsent(threadId, t -> new ConcurrentHashMap<>()).put(name, callback);

    // Register shutdown hook once
    registerShutdownHookIfNeeded();

    log.debug("Registered destruction callback for bean '{}' in thread '{}'", name, threadId);
  }

  @Nullable
  @Override
  public Object resolveContextualObject(String key) {
    return null;
  }

  @Override
  public String getConversationId() {
    // Use identityHashCode for virtual-thread-safe uniqueness
    return CONVERSATION_ID + "-" + System.identityHashCode(Thread.currentThread());
  }

  // ---------------------------------------------------------------------------
  // Cleanup Methods
  // ---------------------------------------------------------------------------

  /**
   * Manual cleanup for thread completion.
   * Must be called after each automation thread finishes.
   */
  private void registerShutdownHookIfNeeded() {
    if (!shutdownHookRegistered) {
      synchronized (this) {
        if (!shutdownHookRegistered) {
          Runtime.getRuntime()
              .addShutdownHook(Thread.ofPlatform()
                  .name(THREAD_PREFIX + "-shutdown")
                  .unstarted(this::executeAllDestructionCallbacks));
          shutdownHookRegistered = true;
          log.debug("Registered AutomationScope shutdown hook");
        }
      }
    }
  }

  /**
   * Execute all destruction callbacks for all threads during shutdown
   */
  private void executeAllDestructionCallbacks() {
    log.trace("Executing AutomationScope destruction callbacks for {} threads", globalDestructionCallbacks.size());

    int totalCallbacks = 0;
    for (Map.Entry<String, Map<String, Runnable>> threadEntry : globalDestructionCallbacks.entrySet()) {
      String threadId = threadEntry.getKey();
      Map<String, Runnable> callbacks = threadEntry.getValue();

      log.debug("Executing {} destruction callbacks for thread '{}'", callbacks.size(), threadId);

      for (Map.Entry<String, Runnable> callbackEntry : callbacks.entrySet()) {
        String beanName = callbackEntry.getKey();
        Runnable callback = callbackEntry.getValue();

        try {
          log.debug("Executing destruction callback for bean '{}' in thread '{}'", beanName, threadId);
          callback.run();
          totalCallbacks++;
        } catch (Exception e) {
          log.error("Error executing destruction callback for bean '{}' in thread '{}': {}",
              beanName,
              threadId,
              e.getMessage(),
              e);
        }
      }
    }

    log.trace("AutomationScope shutdown completed - executed {} destruction callbacks", totalCallbacks);

    // Clear global registry
    globalDestructionCallbacks.clear();
  }

  // ---------------------------------------------------------------------------
  // Public Cleanup API
  // ---------------------------------------------------------------------------

  /**
   * Clear all scoped beans for the current thread.
   * <p>
   * Call this at the end of a test or thread lifecycle to clean up
   * thread-scoped instances and execute their destruction callbacks.
   */
  public void clearCurrentThread() {
    String threadId = getConversationId();
    log.debug("Clearing automation scope for thread: {}", threadId);

    // Execute destruction callbacks for this thread
    Map<String, Runnable> callbacks = destructionCallbacks.get();
    for (Map.Entry<String, Runnable> entry : callbacks.entrySet()) {
      safeRun(entry.getValue());
    }

    // Clear thread-local storage
    scopedObjects.get().clear();
    destructionCallbacks.get().clear();

    // Clear from global registry
    globalDestructionCallbacks.remove(threadId);

    // Also clear from testara-core's RootRegistry for consistency
    try {
      RootRegistry.instance().clearScope(threadId);
    } catch (Exception e) {
      log.trace("Could not clear RootRegistry scope: {}", e.getMessage());
    }

    log.debug("Cleared {} destruction callbacks for thread '{}'", callbacks.size(), threadId);
  }

  /**
   * Get all scoped object names for the current thread.
   * Useful for debugging and testing.
   */
  public java.util.Set<String> getScopedObjectNames() {
    return new java.util.HashSet<>(scopedObjects.get().keySet());
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  private void safeRun(Runnable r) {
    try {
      r.run();
    } catch (Throwable t) {
      log.warn("Error in destruction callback: {}", t.getMessage(), t);
    }
  }
}
