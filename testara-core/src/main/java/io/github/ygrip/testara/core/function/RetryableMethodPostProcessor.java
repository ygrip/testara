package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.factory.PostProcessorRegistry;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.extern.log4j.Log4j2;

/**
 * Facade for configuring method interception in the automation framework.
 * 
 * This class serves as the entry point for enabling @RetryableMethod interception.
 * It configures and registers the MethodInterceptionPostProcessor with the
 * PostProcessorRegistry, enabling ByteBuddy-based method interception.
 * 
 * Usage:
 * <pre>
 * // Option 1: Automatic via TestComponent annotation (registered in RootRegistry)
 * // Just obtain from context - configuration happens automatically
 * RetryableMethodPostProcessor processor = context.get(RetryableMethodPostProcessor.class);
 * 
 * // Option 2: Manual configuration
 * RetryableMethodPostProcessor processor = new RetryableMethodPostProcessor(collector);
 * processor.enable(); // Registers with PostProcessorRegistry
 * </pre>
 * 
 * This implementation is framework-agnostic:
 * - Works standalone without Spring
 * - Works with Spring (via testara-spring integration)
 * - Works with Cucumber (via testara integration)
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class RetryableMethodPostProcessor {

  private final MethodInvocationCollector collector;
  private final MethodInterceptionPostProcessor interceptionPostProcessor;
  private volatile boolean enabled = false;

  /**
   * Create a new RetryableMethodPostProcessor with the given collector.
   * 
   * @param collector the method invocation collector to use for retry logic
   */
  public RetryableMethodPostProcessor(MethodInvocationCollector collector) {
    this.collector = collector;
    this.interceptionPostProcessor = new MethodInterceptionPostProcessor();
    
    // Configure the post processor with package whitelist ONLY
    // DO NOT set explicitCollectorSupplier - we want dynamic lookup at invocation time!
    // This ensures the interceptor gets the correct TEST-scoped collector for the current
    // test/thread, not the collector that was captured during framework initialization.
    this.interceptionPostProcessor.configure(
        null,  // Use dynamic lookup via TestFramework.context().get() at invocation time
        collector.getScanLocations()
    );
    
    // Auto-enable on construction
    enable();
  }

  /**
   * Get the underlying collector.
   * Used as lazy supplier for the interception post processor.
   */
  private MethodInvocationCollector getCollector() {
    return this.collector;
  }

  /**
   * Enable method interception by registering with PostProcessorRegistry.
   * Safe to call multiple times - only registers once.
   */
  public void enable() {
    if (!enabled) {
      synchronized (this) {
        if (!enabled) {
          PostProcessorRegistry.instance().register(interceptionPostProcessor);
          enabled = true;
        }
      }
    }
  }

  /**
   * Check if method interception is enabled.
   * 
   * @return true if interception is active
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Get the underlying MethodInterceptionPostProcessor.
   * Useful for advanced configuration or testing.
   * 
   * @return the method interception post processor
   */
  public MethodInterceptionPostProcessor getInterceptionPostProcessor() {
    return interceptionPostProcessor;
  }

  /**
   * Get the MethodInvocationCollector.
   * 
   * @return the collector used for retry logic
   */
  public MethodInvocationCollector getMethodInvocationCollector() {
    return collector;
  }

  // ========================================================================
  // Legacy API - For backward compatibility with existing code that uses
  // this class like a Spring BeanPostProcessor
  // ========================================================================

  /**
   * Process an instance after initialization.
   * 
   * @deprecated Use the automatic post-processing via PostProcessorRegistry instead.
   *             This method is kept for backward compatibility with code that
   *             manually calls postProcessAfterInitialization.
   * 
   * @param bean     the bean instance
   * @param beanName the bean name (ignored in framework-agnostic mode)
   * @return the processed bean (possibly a proxy)
   */
  @Deprecated
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean == null) {
      return null;
    }
    
    @SuppressWarnings("unchecked")
    Class<Object> beanClass = (Class<Object>) bean.getClass();
    
    // Delegate to the new implementation
    return interceptionPostProcessor.postProcess(bean, beanClass);
  }

  /**
   * Convenience method to get an instance from the current test context.
   * Creates and registers automatically if not yet available.
   * 
   * @return RetryableMethodPostProcessor instance for the current test scope
   */
  public static RetryableMethodPostProcessor instance() {
    return TestFramework.context().get(RetryableMethodPostProcessor.class);
  }
}
