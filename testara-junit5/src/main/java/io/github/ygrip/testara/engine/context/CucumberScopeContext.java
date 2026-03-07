package io.github.ygrip.testara.engine.context;

import io.github.ygrip.testara.core.registry.ScopeContext;

/**
 * ScopeContext implementation for Cucumber scenario execution.
 * <p>
 * Provides scope key resolution based on:
 * - Current feature (for feature-level scope)
 * - Current scenario (for scenario-level scope)
 * - Thread identity (for parallel execution)
 * <p>
 * This is registered via SPI and takes precedence in Cucumber test execution.
 */
public final class CucumberScopeContext implements ScopeContext {

  private static final String DEFAULT_SCOPE = "cucumber-default";
  private static final String SCOPE_PREFIX = "cucumber-";

  // Thread-local for scenario scope (scenario unique ID)
  private static final ThreadLocal<String> CURRENT_SCENARIO = new InheritableThreadLocal<>();

  // Thread-local for feature scope (feature name/path)
  private static final ThreadLocal<String> CURRENT_FEATURE = new InheritableThreadLocal<>();

  /**
   * Enter feature scope (called before feature execution).
   *
   * @param featureId unique identifier for the feature
   */
  public static void enterFeature(String featureId) {
    CURRENT_FEATURE.set(featureId);
  }

  /**
   * Get the current feature scope identifier.
   *
   * @return the current feature ID or null if not in a feature
   */
  public static String getCurrentFeature() {
    return CURRENT_FEATURE.get();
  }

  /**
   * Exit feature scope (called after feature execution).
   */
  public static void exitFeature() {
    CURRENT_FEATURE.remove();
  }

  /**
   * Enter scenario scope (called before scenario execution).
   *
   * @param scenarioId unique identifier for the scenario (pickle ID or unique ID)
   */
  public static void enterScenario(String scenarioId) {
    CURRENT_SCENARIO.set(scenarioId);
  }

  /**
   * Get the current scenario scope identifier.
   *
   * @return the current scenario ID or null if not in a scenario
   */
  public static String getCurrentScenario() {
    return CURRENT_SCENARIO.get();
  }

  /**
   * Get the current scope key with prefix.
   * This matches the key used for caching scoped instances.
   *
   * @return the current scope key (prefixed), or default scope if not in a scenario
   */
  public static String getCurrentScopeKey() {
    String scenarioScope = CURRENT_SCENARIO.get();
    if (scenarioScope != null) {
      return SCOPE_PREFIX + scenarioScope;
    }
    String featureScope = CURRENT_FEATURE.get();
    if (featureScope != null) {
      return SCOPE_PREFIX + featureScope;
    }
    return DEFAULT_SCOPE;
  }

  /**
   * Exit scenario scope (called after scenario execution).
   */
  public static void exitScenario() {
    CURRENT_SCENARIO.remove();
  }

  /**
   * Clear all scope context (called at test run end).
   */
  public static void clearAll() {
    CURRENT_SCENARIO.remove();
    CURRENT_FEATURE.remove();
  }

  @Override
  public String currentScopeKey() {
    // Prefer scenario scope (most specific)
    String scenarioScope = CURRENT_SCENARIO.get();
    if (scenarioScope != null) {
      return SCOPE_PREFIX + scenarioScope;
    }

    // Fall back to feature scope
    String featureScope = CURRENT_FEATURE.get();
    if (featureScope != null) {
      return SCOPE_PREFIX + featureScope;
    }

    // Fallback to default scope for edge cases (e.g., before any scenario starts)
    return DEFAULT_SCOPE;
  }
}
