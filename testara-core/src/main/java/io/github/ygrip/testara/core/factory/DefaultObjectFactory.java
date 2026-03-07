package io.github.ygrip.testara.core.factory;

/**
 * Default non-Spring ObjectFactory implementation.
 * Uses constructor-based instantiation with recursive dependency resolution.
 * This factory does NOT check RootRegistry - that's handled by the caller.
 * It purely focuses on creating instances via constructor injection.
 */
public final class DefaultObjectFactory implements ObjectFactory {

  @Override
  public <T> T getInstance(Class<T> type) {
    // Pure constructor resolution - no registry checks
    // Registry checks should be done by the caller (RootRegistry or InstanceResolver)
    return new InstanceResolver().resolve(type);
  }

  @Override
  public int priority() {
    return 0; // Lowest priority - default fallback
  }
}
