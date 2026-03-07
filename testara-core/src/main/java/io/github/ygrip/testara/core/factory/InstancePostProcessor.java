package io.github.ygrip.testara.core.factory;

/**
 * SPI for post-processing newly created instances.
 * Implementations can modify or wrap instances after creation.
 * 
 * This is a framework-agnostic alternative to Spring's BeanPostProcessor.
 * Implementations are loaded via Java ServiceLoader for auto-discovery.
 * 
 * Use cases:
 * - Method interception (e.g., ByteBuddy proxies for @RetryableMethod)
 * - AOP-like behavior
 * - Instance decoration/wrapping
 * 
 * @author yunaz.ramadhan
 */
public interface InstancePostProcessor {

  /**
   * Process an instance after it has been created and before it's returned to the caller.
   * Implementations can return a modified/wrapped instance or the original.
   *
   * @param instance     the newly created instance
   * @param instanceType the original type that was requested
   * @param <T>          the instance type
   * @return the processed instance (may be the same or a proxy/wrapper)
   */
  <T> T postProcess(T instance, Class<T> instanceType);

  /**
   * Determine if this post processor should be applied to the given type.
   * Return false to skip processing for types that don't need it.
   *
   * @param type the class to check
   * @return true if this processor should process instances of this type
   */
  default boolean supports(Class<?> type) {
    return true;
  }

  /**
   * Priority for post processor ordering. Higher priority processors run first.
   * Useful when multiple processors need to be applied in a specific order.
   *
   * @return priority value (higher = runs earlier)
   */
  default int priority() {
    return 0;
  }

  /**
   * Called when the post processor is initialized.
   * Use this for lazy initialization of resources.
   */
  default void initialize() {
    // no-op by default
  }

  /**
   * Called when the post processor is being shut down.
   * Use this to clean up resources.
   */
  default void shutdown() {
    // no-op by default
  }
}

