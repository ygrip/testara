package io.github.ygrip.testara.core.registry;

import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.core.factory.ObjectFactoryLoader;
import org.apache.commons.lang3.ObjectUtils;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for managing scoped component providers.
 * 
 * Architecture:
 * - STATIC providers/mappedTypes: Shared across ALL threads for provider registration visibility
 * - ThreadLocal scopeContexts: Each thread resolves its own scope key for proper isolation
 * - ScopedProvider.cache: Keyed by scope name, providing instance isolation per test/thread
 * 
 * This design supports:
 * - Parallel test execution (providers visible across ForkJoin pool threads)
 * - Test isolation (each test gets its own instances via unique scope keys)
 * - Thread isolation (THREAD-scoped components isolated per thread)
 */
public final class RootRegistry {

  // STATIC: Providers and type mappings are shared across ALL threads
  // This ensures providers registered in one thread are visible in parallel test threads
  private static final Map<Class<?>, ScopedProvider<?>> providers = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Class<?>> mappedTypes = new ConcurrentHashMap<>();
  
  // Lazy-initialized singleton for ObjectFactory (thread-safe via holder pattern)
  private static volatile ObjectFactory sharedFactory;
  private static final Object FACTORY_LOCK = new Object();

  // ThreadLocal: Each thread has its own scope context for resolving scope keys
  // This ensures proper isolation - different tests on different threads get different scope keys
  private static final ThreadLocal<ScopeContext> testScopeContext = 
      ThreadLocal.withInitial(ScopeContextLoader::load);
  private static final ThreadLocal<ScopeContext> threadScopeContext = 
      ThreadLocal.withInitial(ThreadScopeContext::new);

  // Singleton instance (stateless after static fields)
  private static final RootRegistry INSTANCE = new RootRegistry();

  private RootRegistry() {
    // Private constructor for singleton
  }

  public static RootRegistry instance() {
    return INSTANCE;
  }
  
  private static ObjectFactory getSharedFactory() {
    if (sharedFactory == null) {
      synchronized (FACTORY_LOCK) {
        if (sharedFactory == null) {
          sharedFactory = ObjectFactoryLoader.load();
        }
      }
    }
    return sharedFactory;
  }

  public ObjectFactory factory() {
    return getSharedFactory();
  }

  /**
   * Register a type for lazy instantiation.
   * Instances will be created per scope using the ObjectFactory.
   * Use this for TEST and THREAD scoped components.
   */
  public <T> void register(Class<T> type, RegistryScope scope) {
    // Don't eagerly create instance - register the type for lazy creation
    ScopedProvider<T> provider = new ScopedProvider<>(scope);
    
    // Map the interfaces if has any
    for (Class<?> iFace : type.getInterfaces()) {
      if (!mappedTypes.containsKey(iFace)) {
        mappedTypes.put(iFace, type);
      }
    }

    // Map by abstract class if has any
    Class<?> abstractClass = findAbstractSuperclass(type);
    if (ObjectUtils.isNotEmpty(abstractClass)) {
      if (!mappedTypes.containsKey(abstractClass)) {
        mappedTypes.put(abstractClass, type);
      }
    }

    // Register by concrete class
    providers.put(type, provider);
  }

  /**
   * Register a pre-created instance.
   * For GLOBAL scope: the same instance is shared across all scopes.
   * For TEST/THREAD scope: this specific instance is used (not recommended, prefer register(Class, scope)).
   */
  public <T> void register(T instance, RegistryScope scope) {
    Class<?> concreteClass = instance.getClass();
    ScopedProvider<T> provider = new ScopedProvider<>(scope, () -> instance);

    // Map the interfaces if has any
    for (Class<?> iFace : concreteClass.getInterfaces()) {
      if (!mappedTypes.containsKey(iFace)) {
        mappedTypes.put(iFace, concreteClass);
      }
    }

    // Map by abstract class if has any
    Class<?> abstractClass = findAbstractSuperclass(concreteClass);
    if (ObjectUtils.isNotEmpty(abstractClass)) {
      if (!mappedTypes.containsKey(abstractClass)) {
        mappedTypes.put(abstractClass, concreteClass);
      }
    }

    // Register by concrete class
    providers.put(concreteClass, provider);
  }

  /**
   * Register a pre-created instance, overriding any existing abstract/interface
   * mappings. Use when a specific concrete implementation must take precedence
   * (e.g. {@code MitmProxySeleniumUtility} replacing {@code BrowserUpProxyUtility}
   * as the active {@code AbstractProxy}).
   */
  public <T> void registerOverride(T instance, RegistryScope scope) {
    Class<?> concreteClass = instance.getClass();
    ScopedProvider<T> provider = new ScopedProvider<>(scope, () -> instance);

    for (Class<?> iFace : concreteClass.getInterfaces()) {
      mappedTypes.put(iFace, concreteClass);
    }

    Class<?> abstractClass = findAbstractSuperclass(concreteClass);
    if (ObjectUtils.isNotEmpty(abstractClass)) {
      mappedTypes.put(abstractClass, concreteClass);
    }

    providers.put(concreteClass, provider);
  }

  Class<?> findAbstractSuperclass(Class<?> concrete) {
    Class<?> current = concrete.getSuperclass();
    while (current != null && current != Object.class) {
      if (Modifier.isAbstract(current.getModifiers())) {
        return current;
      }
      current = current.getSuperclass();
    }
    return null;
  }


  /**
   * Get an instance using the default scope resolution (from ScopeContext).
   */
  public <T> T get(Class<T> type) {
    ScopedProvider<T> provider = resolveProvider(type);
    Class<?> concreteType = resolveConcreteType(type);
    
    @SuppressWarnings("unchecked")
    final Class<T> typeToInstantiate = (Class<T>) concreteType;
    return provider.get(resolveScopeName(provider), () -> getSharedFactory().getInstance(typeToInstantiate));
  }

  /**
   * Get an instance using an explicit scope key.
   * This is used by TestContext to ensure scope isolation.
   */
  public <T> T get(Class<T> type, String scopeKey) {
    ScopedProvider<T> provider = resolveProvider(type);
    Class<?> concreteType = resolveConcreteType(type);
    
    @SuppressWarnings("unchecked")
    final Class<T> typeToInstantiate = (Class<T>) concreteType;
    return provider.get(scopeKey, () -> getSharedFactory().getInstance(typeToInstantiate));
  }

  @SuppressWarnings("unchecked")
  private <T> ScopedProvider<T> resolveProvider(Class<T> type) {
    ScopedProvider<T> provider = null;

    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      Class<?> resolved = mappedTypes.get(type);
      if (resolved != null) {
        provider = (ScopedProvider<T>) providers.get(resolved);
      }
    } else {
      provider = (ScopedProvider<T>) providers.get(type);
    }
    
    if (provider == null) {
      throw new IllegalStateException("No provider registered for " + type.getName());
    }
    return provider;
  }

  private Class<?> resolveConcreteType(Class<?> type) {
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      Class<?> resolved = mappedTypes.get(type);
      if (resolved != null) {
        return resolved;
      }
    }
    return type;
  }

  private String resolveScopeName(ScopedProvider<?> provider) {
    return switch (provider.scope()) {
      case GLOBAL -> RegistryScope.GLOBAL.name();
      case THREAD -> threadScopeContext.get().currentScopeKey();
      case TEST -> testScopeContext.get().currentScopeKey();
    };
  }

  public void clearCurrentTestScope() {
    String testId = testScopeContext.get().currentScopeKey();
    providers.values().forEach(p -> p.clear(testId));
  }

  public void clearScope(String scopeName) {
    providers.values().forEach(p -> p.clear(scopeName));
  }

  /**
   * Check if an instance exists in cache for the current scope.
   * Does NOT create instances - only checks the cache.
   */
  public boolean hasInstance(Class<?> type) {
    Class<?> resolved = mappedTypes.getOrDefault(type, type);
    ScopedProvider<?> scopedProvider = providers.get(resolved);
    if (ObjectUtils.isEmpty(scopedProvider)) {
      return false;
    }
    return scopedProvider.hasInstance(resolveScopeName(scopedProvider));
  }

  /**
   * Check if an instance exists in cache for a specific scope key.
   * Does NOT create instances - only checks the cache.
   */
  public boolean hasInstance(Class<?> type, String scopeKey) {
    Class<?> resolved = mappedTypes.getOrDefault(type, type);
    ScopedProvider<?> scopedProvider = providers.get(resolved);
    if (ObjectUtils.isEmpty(scopedProvider)) {
      return false;
    }
    return scopedProvider.hasInstance(scopeKey);
  }

  /**
   * Check if a provider is registered for the given type.
   * This does NOT check if an instance is cached.
   */
  public boolean hasProvider(Class<?> type) {
    Class<?> resolved = mappedTypes.getOrDefault(type, type);
    return providers.containsKey(resolved);
  }

  /**
   * Clear all providers and mappings.
   * CAUTION: This is primarily for testing to reset state between test classes.
   * In production, providers should be registered once and reused.
   */
  public static void clearAll() {
    providers.clear();
    mappedTypes.clear();
  }

  /**
   * Clear ThreadLocal scope contexts for the current thread.
   * Call this when a thread is done to prevent memory leaks.
   */
  public static void clearThreadLocals() {
    testScopeContext.remove();
    threadScopeContext.remove();
  }
}

