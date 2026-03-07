package io.github.ygrip.testara.core.factory;

/**
 * SPI-based factory for object instantiation.
 * Implementations should be registered via Java ServiceLoader.
 */
public interface ObjectFactory {

  /**
   * Get an instance of the specified type.
   * Implementation should handle:
   * - Checking RootRegistry for registered providers
   * - Creating new instances if not registered
   * - Resolving dependencies recursively
   *
   * @param type the class to instantiate
   * @param <T>  the type
   * @return an instance of the specified type
   */
  <T> T getInstance(Class<T> type);

  /**
   * Check if this factory supports creating instances of the given type.
   * Used for factory selection when multiple implementations are available.
   *
   * @param type the class to check
   * @return true if this factory can create instances of the type
   */
  default boolean supports(Class<?> type) {
    return true;
  }

  /**
   * Priority for factory selection. Higher priority factories are preferred.
   * Useful when multiple factories support the same type.
   *
   * @return priority value (higher = preferred)
   */
  default int priority() {
    return 0;
  }

  /**
   * Optional lifecycle hook called when the factory is initialized.
   */
  default void start() {
    // no-op by default
  }

  /**
   * Optional lifecycle hook called when the factory is shut down.
   */
  default void stop() {
    // no-op by default
  }
}
