package io.github.ygrip.testara.engine.context;

import io.github.ygrip.testara.core.registry.ScopeContext;

import java.util.UUID;

/**
 * ScopeContext implementation for Cucumber scenario execution.
 * <p>
 * Provides scope key resolution based on:
 * - Current feature (for feature-level scope)
 * - Current scenario (for scenario-level scope)
 * - Thread identity (for parallel execution)
 * <p>
 * This is registered via SPI and takes precedence in Cucumber test execution.
 * <p>
 * Two distinct scope-binding modes are supported:
 * <ul>
 *   <li><b>Run scope</b> (sequential execution): {@link #enterRunScope()} binds the current
 *       thread to a single shared key generated once per framework run via
 *       {@link #startNewRun()}. All scenarios in a sequential run therefore share the same
 *       TEST-scoped instances.</li>
 *   <li><b>Scenario scope</b> (parallel execution): {@link #enterScenario(String)} binds the
 *       current thread to a scenario-specific key so concurrently running scenarios get
 *       isolated TEST-scoped instances.</li>
 * </ul>
 */
public final class CucumberScopeContext implements ScopeContext {

  private static final String DEFAULT_SCOPE = "cucumber-default";
  private static final String SCOPE_PREFIX = "cucumber-";

  // Thread-local for scenario scope (scenario unique ID, or the shared run-scope id)
  private static final ThreadLocal<String> CURRENT_SCENARIO = new InheritableThreadLocal<>();

  // Thread-local for feature scope (feature name/path)
  private static final ThreadLocal<String> CURRENT_FEATURE = new InheritableThreadLocal<>();

  // Raw (unprefixed) id shared by every scenario in a sequential run. Regenerated once per
  // framework (re)initialization via startNewRun() so state does not leak between separate
  // engine executions within the same JVM.
  private static volatile String runScopeId = "run-" + UUID.randomUUID();

  /**
   * Generate a fresh run-scope id. Call once per framework initialization (sequential mode
   * only), typically from the winning thread of the init race in the extension's beforeAll.
   *
   * @return the resulting run-scope key (prefixed), same value {@link #runScopeKey()} returns
   */
  public static String startNewRun() {
    runScopeId = "run-" + UUID.randomUUID();
    return runScopeKey();
  }

  /**
   * @return the current run-scope key (prefixed) shared by all scenarios in a sequential run
   */
  public static String runScopeKey() {
    return SCOPE_PREFIX + runScopeId;
  }

  /**
   * Bind the current thread to the shared run-scope key. Used for sequential execution where
   * every scenario in the run must resolve to the same TEST scope. Idempotent — always binds
   * the same value, so it is safe to call again for every scenario.
   */
  public static void enterRunScope() {
    CURRENT_SCENARIO.set(runScopeId);
  }

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
   * Enter scenario scope (called before scenario execution). This is only meaningful for
   * <b>parallel</b> execution — each concurrently running scenario must get its own isolated
   * TEST scope key. For sequential execution use {@link #enterRunScope()} instead.
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
