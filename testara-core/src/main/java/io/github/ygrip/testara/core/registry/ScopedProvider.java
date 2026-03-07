package io.github.ygrip.testara.core.registry;

import org.apache.commons.lang3.ObjectUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ScopedProvider<T> {

  private final RegistryScope scope;
  private final ConcurrentHashMap<String, T> cache = new ConcurrentHashMap<>();
  private final Supplier<T> fixedSupplier;

  /**
   * Create a provider without a fixed supplier.
   * Instances will be created lazily per scope using the creator passed to get().
   */
  public ScopedProvider(RegistryScope scope) {
    this(scope, null);
  }

  /**
   * Create a provider with a fixed supplier.
   * For GLOBAL scope with a fixed instance, the supplier always returns the same instance.
   * For other scopes, consider using the no-arg constructor to create instances per scope.
   */
  public ScopedProvider(RegistryScope scope, Supplier<T> fixedSupplier) {
    this.scope = scope;
    this.fixedSupplier = fixedSupplier;
  }

  public RegistryScope scope() {
    return scope;
  }

  /**
   * Get or create an instance for the given scope.
   * If this provider has a fixed supplier AND scope is GLOBAL, uses the fixed supplier.
   * Otherwise, creates instances per scope using the creator.
   */
  public T get(String scopeName, Supplier<T> creator) {
    String key = scopeName != null ? scopeName : RegistryScope.GLOBAL.name();

    // For GLOBAL scope with fixed supplier, share the same instance across all keys
    if (scope == RegistryScope.GLOBAL && fixedSupplier != null) {
      return cache.computeIfAbsent(key, k -> fixedSupplier.get());
    }

    // For TEST/THREAD scope, always use the creator to get scope-specific instances
    return cache.computeIfAbsent(key, k -> creator.get());
  }

  /**
   * Peek at the cached instance without creating one.
   * Returns null if no instance is cached for this scope.
   */
  public T peek(String scopeName) {
    String key = scopeName != null ? scopeName : RegistryScope.GLOBAL.name();
    // Just peek - don't create anything
    if (key.equals(RegistryScope.GLOBAL.name())) {
      return fixedSupplier.get();
    } else {
      return cache.get(key);
    }
  }

  /**
   * Check if a provider is registered (not necessarily instantiated).
   */
  public boolean isRegistered() {
    return true; // If this provider exists, it's registered
  }

  /**
   * Check if an instance exists in the cache for the given scope.
   */
  public boolean hasInstance(String scopeName) {
    String key = scopeName != null ? scopeName : RegistryScope.GLOBAL.name();
    if (key.equals(RegistryScope.GLOBAL.name())) {
      return ObjectUtils.isNotEmpty(fixedSupplier);
    } else {
      return cache.containsKey(key);
    }
  }

  public void clear(String scopeName) {
    if (scopeName != null) {
      cache.remove(scopeName);
    }
  }
}

