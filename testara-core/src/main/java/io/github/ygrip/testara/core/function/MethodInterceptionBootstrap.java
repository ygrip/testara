package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.factory.PostProcessorRegistry;
import lombok.extern.log4j.Log4j2;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstrap utility for method interception.
 *
 * Provides a simple way to enable @RetryableMethod interception in the framework.
 * Call {@link #enable()} or {@link #enable(Set)} early in test initialization.
 *
 * Usage examples:
 *
 * <pre>
 * // Option 1: Enable with default settings (uses dynamic collector lookup)
 * MethodInterceptionBootstrap.enable();
 *
 * // Option 2: Enable with specific package whitelist
 * MethodInterceptionBootstrap.enable(Set.of("com.mycompany.tests"));
 *
 * // Option 3: Automatic via @TestComponent (when RetryableMethodPostProcessor is instantiated)
 * // Just use TestContext.get(RetryableMethodPostProcessor.class) - it auto-enables
 * </pre>
 *
 * This class is thread-safe and idempotent - calling enable() multiple times is safe.
 */
@Log4j2
public final class MethodInterceptionBootstrap {

  private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
  private static volatile MethodInterceptionPostProcessor postProcessor;

  private MethodInterceptionBootstrap() {
    // Utility class
  }

  /**
   * Enable method interception with default settings.
   * Uses dynamic collector lookup via TestFramework.context().
   *
   * Safe to call multiple times - only enables once.
   */
  public static void enable() {
    enable(null);
  }

  /**
   * Enable method interception with a specific package whitelist.
   * Only classes in whitelisted packages will be checked for @RetryableMethod.
   *
   * @param whitelistedPackages packages to scan (null or empty for all packages)
   */
  public static void enable(Set<String> whitelistedPackages) {
    if (ENABLED.compareAndSet(false, true)) {
      postProcessor = new MethodInterceptionPostProcessor();
      postProcessor.configure(null, whitelistedPackages); // null supplier = dynamic lookup
      PostProcessorRegistry.instance().register(postProcessor);
    }
  }

  /**
   * Check if method interception is enabled.
   *
   * @return true if interception has been enabled
   */
  public static boolean isEnabled() {
    return ENABLED.get();
  }

  /**
   * Get the post processor instance (if enabled).
   *
   * @return the post processor, or null if not enabled
   */
  public static MethodInterceptionPostProcessor getPostProcessor() {
    return postProcessor;
  }

  /**
   * Disable method interception and clean up resources.
   * Primarily for testing purposes.
   */
  public static void disable() {
    if (ENABLED.compareAndSet(true, false)) {
      if (postProcessor != null) {
        PostProcessorRegistry.instance().unregister(postProcessor);
        postProcessor = null;
      }
    }
  }

  /**
   * Reset to initial state.
   * Clears all caches and disables interception.
   * Primarily for testing purposes.
   */
  public static void reset() {
    disable();
    MethodInterceptionPostProcessor.clearCache();
    PostProcessorRegistry.instance().shutdown();
    log.debug("MethodInterceptionBootstrap reset complete");
  }
}

