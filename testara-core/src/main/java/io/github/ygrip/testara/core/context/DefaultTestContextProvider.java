package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.config.TestConfiguration;

/**
 * Default implementation of TestContextProvider.
 * Creates DefaultTestContext instances.
 * <p>
 * This provider has the lowest priority (0) and is used as a fallback
 * when no other provider (like Spring) is available.
 */
public final class DefaultTestContextProvider implements TestContextProvider {

  @Override
  public TestContext create(TestConfiguration configuration, String scopeId) {
    return new DefaultTestContext(configuration, scopeId);
  }

  @Override
  public int priority() {
    return 0; // Lowest priority - fallback provider
  }

  @Override
  public boolean isAvailable() {
    return true; // Always available
  }
}
