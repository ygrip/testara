package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.config.TestConfiguration;

/**
 * SPI for providing TestContext instances.
 * Implementations should be registered via Java ServiceLoader.
 * <p>
 * This allows different modules (e.g., testara-spring) to provide
 * their own TestContext implementations without testara-junit5
 * needing to know about them.
 */
public interface TestContextProvider {

  /**
   * Create a TestContext instance.
   *
   * @param configuration the test configuration
   * @param scopeId       the unique scope identifier for this context
   * @return a configured TestContext instance
   */
  TestContext create(TestConfiguration configuration, String scopeId);

  /**
   * Create a TestContext instance with auto-generated scope ID.
   *
   * @param configuration the test configuration
   * @return a configured TestContext instance
   */
  default TestContext create(TestConfiguration configuration) {
    return create(configuration, java.util.UUID.randomUUID().toString());
  }

  /**
   * Priority for provider selection. Higher priority providers are preferred.
   * Spring-backed providers should return higher values.
   *
   * @return priority value (higher = preferred)
   */
  default int priority() {
    return 0;
  }

  /**
   * Check if this provider is available in the current environment.
   * For example, Spring provider checks if ApplicationContext is available.
   *
   * @return true if this provider can create contexts
   */
  default boolean isAvailable() {
    return true;
  }
}
