package io.github.ygrip.testara.core.registry;

/**
 * SPI for providing scope identification in different contexts.
 * Implementations provide unique keys for scope isolation.
 */
public interface ScopeContext {
  
  /**
   * Get the current scope key for this context.
   * The scope key is used to isolate instances in the registry.
   *
   * @return a unique scope identifier (never null)
   */
  String currentScopeKey();
}
