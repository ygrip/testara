package io.github.ygrip.testara.core.registry;

/**
 * ScopeContext that uses thread name as the scope key.
 * Provides thread-level isolation for instances.
 */
public final class ThreadScopeContext implements ScopeContext {
  
  @Override
  public String currentScopeKey() {
    return Thread.currentThread().getName();
  }
}
