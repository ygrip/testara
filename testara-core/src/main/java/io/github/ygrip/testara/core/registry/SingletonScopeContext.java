package io.github.ygrip.testara.core.registry;

/**
 * Default ScopeContext that always returns the global scope.
 * Used when no test framework is present.
 */
public final class SingletonScopeContext implements ScopeContext {
  
  @Override
  public String currentScopeKey() {
    return RegistryScope.GLOBAL.name();
  }
}
