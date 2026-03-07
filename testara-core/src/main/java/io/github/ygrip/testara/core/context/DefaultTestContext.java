package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.core.registry.ScopeContext;
import io.github.ygrip.testara.core.registry.ScopeContextLoader;

import java.util.UUID;

/**
 * Default implementation of TestContext.
 * <p>
 * Supports two modes of scope resolution:
 * <ul>
 *   <li><b>Dynamic scope</b>: When a ScopeContext has an active scope set (e.g., via 
 *       CucumberScopeContext.enterScenario() or JUnit5ScopeContext.enter()), 
 *       uses RootRegistry's dynamic scope resolution for parallel test isolation.</li>
 *   <li><b>Explicit scope</b>: When no dynamic scope is active, falls back to this 
 *       context's own scopeId for explicit scope control.</li>
 * </ul>
 * </p>
 * <p>
 * This design supports both:
 * - Cucumber parallel execution (shared TestContext, dynamic per-thread scope)
 * - JUnit5 tests with explicit TestContext instances (each with its own scope)
 * </p>
 */
public class DefaultTestContext implements TestContext {
  private final ObjectFactory factory;
  private final TestConfiguration configuration;
  private final String scopeId;
  private final ObjectConverter parser;
  private final ScopeContext scopeContext;

  public DefaultTestContext(TestConfiguration configuration) {
    this(configuration, UUID.randomUUID().toString());
  }

  public DefaultTestContext(TestConfiguration configuration, String scopeId) {
    this.factory = RootRegistry.instance().factory();
    this.configuration = configuration;
    this.scopeId = scopeId;
    this.parser = ObjectConverterLoader.instance();
    this.scopeContext = ScopeContextLoader.load();
  }

  /**
   * Get the unique scope identifier for this test context.
   * Used as fallback when no dynamic scope is active.
   */
  public String scopeId() {
    return scopeId;
  }

  @Override
  public ObjectFactory factory() {
    return factory;
  }

  @Override
  public ObjectConverter converter() {
    return parser;
  }

  @Override
  public TestConfiguration configuration() {
    return configuration;
  }

  /**
   * Get an instance of the specified type with TEST scope.
   * <p>
   * Uses dynamic scope resolution when available (for parallel execution),
   * or falls back to this context's explicit scopeId when no dynamic scope is set.
   * </p>
   */
  @Override
  public <T> T get(Class<T> type) {
    return get(type, RegistryScope.TEST);
  }

  /**
   * Get an instance of the specified type with the given scope.
   * <p>
   * For TEST scope: checks if a dynamic scope is active via ScopeContext.
   * If active, uses RootRegistry's dynamic resolution for parallel isolation.
   * If not active (default scope), uses this context's explicit scopeId.
   * </p>
   */
  public <T> T get(Class<T> type, RegistryScope scope) {
    if (scope == RegistryScope.TEST && hasDynamicScope()) {
      // Dynamic scope is active - use RootRegistry's scope resolution
      // This supports Cucumber parallel execution where each thread has its own scope
      return RootRegistry.instance().get(type);
    }
    // No dynamic scope or non-TEST scope - use explicit scope key
    return RootRegistry.instance().get(type, resolveScopeKey(scope));
  }

  /**
   * Check if a dynamic scope is currently active.
   * A dynamic scope is considered active if the ScopeContext returns
   * something other than the default/fallback scope.
   */
  private boolean hasDynamicScope() {
    String currentScope = scopeContext.currentScopeKey();
    // Check if scope is not a default/fallback value
    // Common default patterns: "default", "*-default", "singleton"
    return currentScope != null 
        && !currentScope.endsWith("-default") 
        && !currentScope.equals("default")
        && !currentScope.equals("singleton");
  }

  private String resolveScopeKey(RegistryScope scope) {
    return switch (scope) {
      case GLOBAL -> RegistryScope.GLOBAL.name();
      case THREAD -> Thread.currentThread().getName();
      case TEST -> scopeId();
    };
  }

  @Override
  public boolean has(Class<?> type) {
    // Check if there's a provider registered for this type
    return RootRegistry.instance().hasProvider(type);
  }
}
