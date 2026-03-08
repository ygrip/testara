package io.github.ygrip.testara.ui.proxy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.ygrip.testara.core.context.ResourceShutdownRegistry;
import lombok.extern.log4j.Log4j2;

/**
 * Thread-local manager for the active {@link AbstractProxy} instance.
 * <p>
 * Mirrors the {@link io.github.ygrip.testara.ui.driver.DriverSessionManager} pattern:
 * each test thread holds a reference to the proxy that was created during driver setup,
 * so that cucumber step definitions can reliably obtain the correct proxy regardless of
 * which concrete implementation (BrowserUp, MitmProxy) was chosen.
 * <p>
 * Additionally, tracks all proxy instances globally so they can be cleaned up at the end
 * of a test run via {@link #shutdownAll()}.  A JVM shutdown hook is registered as a
 * safety net for abnormal termination.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>{@code ProxyFactory.create()} calls {@link #setCurrentProxy(AbstractProxy)} after starting the proxy</li>
 *   <li>Step definitions call {@link #currentProxy()} to interact with it</li>
 *   <li>{@link #tearDown()} cleans up at the end of a single test</li>
 *   <li>{@link #shutdownAll()} cleans up all remaining proxies at the end of the run</li>
 * </ol>
 */
@Log4j2
public final class ProxyInstanceManager {

  private static final ThreadLocal<AbstractProxy<?>> PROXY_THREAD_LOCAL = new ThreadLocal<>();

  private static final Set<AbstractProxy<?>> ALL_PROXIES = ConcurrentHashMap.newKeySet();
  private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);
  private static final AtomicBoolean SHUTDOWN_INITIATED = new AtomicBoolean(false);

  private ProxyInstanceManager() {
  }

  /**
   * Get the proxy instance associated with the current test thread.
   *
   * @return the active proxy, or {@code null} if none has been registered
   */
  @SuppressWarnings("rawtypes")
  public static AbstractProxy currentProxy() {
    return PROXY_THREAD_LOCAL.get();
  }

  /**
   * Register a proxy as the active instance for the current test thread.
   * Called by {@link io.github.ygrip.testara.ui.factory.ProxyFactory} implementations
   * after the proxy has been started.
   */
  public static void setCurrentProxy(AbstractProxy<?> proxy) {
    log.debug("Registering proxy instance for current thread: {}", proxy != null ? proxy.getClass().getSimpleName() : "null");
    PROXY_THREAD_LOCAL.set(proxy);
    if (proxy != null) {
      ALL_PROXIES.add(proxy);
      ensureShutdownHookRegistered();
    }
  }

  /**
   * Whether a proxy instance is registered and started for the current thread.
   */
  public static boolean hasProxy() {
    AbstractProxy<?> proxy = PROXY_THREAD_LOCAL.get();
    return proxy != null && proxy.isStarted();
  }

  /**
   * Per-scenario cleanup.
   * <p>
   * Calls {@link AbstractProxy#afterScenario()} on the current proxy.  If the proxy
   * is still started afterwards (e.g. MitmProxy reuse), the ThreadLocal and global
   * tracking are preserved so the next scenario on this thread can reuse it.
   * If the proxy stopped itself (e.g. BrowserUp), references are removed.
   */
  public static void tearDown() {
    AbstractProxy<?> proxy = PROXY_THREAD_LOCAL.get();
    if (proxy == null) {
      return;
    }
    try {
      log.debug("Running afterScenario on proxy: {}", proxy.getClass().getSimpleName());
      proxy.afterScenario();
    } catch (Exception e) {
      log.warn("Error in proxy afterScenario: {}", e.getMessage());
    }

    if (!proxy.isStarted()) {
      ALL_PROXIES.remove(proxy);
      PROXY_THREAD_LOCAL.remove();
    }
  }

  /**
   * Destroy all proxy instances that are still tracked (across all threads).
   * <p>
   * Intended to be called once at the end of the test run — either from
   * {@code TestaraFrameworkExtension.afterAll()} or from the JVM shutdown hook.
   * Calling this method more than once is safe (idempotent).
   */
  public static void shutdownAll() {
    if (SHUTDOWN_INITIATED.getAndSet(true)) {
      return;
    }
    if (ALL_PROXIES.isEmpty()) {
      log.debug("No proxy instances to shut down");
      return;
    }

    log.info("Shutting down all remaining proxy instances ({} tracked)", ALL_PROXIES.size());
    for (AbstractProxy<?> proxy : ALL_PROXIES) {
      try {
        if (proxy.isStarted()) {
          log.debug("Stopping proxy: {}", proxy.getClass().getSimpleName());
          proxy.stop();
        }
      } catch (Exception e) {
        log.warn("Error stopping proxy {} during shutdownAll: {}", proxy.getClass().getSimpleName(), e.getMessage());
      }
    }
    ALL_PROXIES.clear();
    log.debug("All proxy instances shut down");
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
      ResourceShutdownRegistry.register("proxy-instances", ProxyInstanceManager::shutdownAll);
      log.debug("Proxy instance shutdown callback registered");
    }
  }
}
