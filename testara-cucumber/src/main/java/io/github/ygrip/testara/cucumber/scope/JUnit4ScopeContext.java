package io.github.ygrip.testara.cucumber.scope;

import io.github.ygrip.testara.core.registry.ScopeContext;

/**
 * ScopeContext implementation for JUnit 4 Cucumber runner.
 * <p>
 * Provides scope key resolution for:
 * - Scenario-level isolation (each scenario gets unique scope)
 * - Thread-level fallback (for parallel execution)
 * <p>
 * This is registered via SPI and is used when running Cucumber
 * with JUnit 4's {@code @RunWith(Cucumber.class)}.
 */
public final class JUnit4ScopeContext implements ScopeContext {

  private static final String DEFAULT_SCOPE = "junit4-default";
  private static final String SCOPE_PREFIX = "junit4-";

  // Thread-local for scenario scope
  private static final ThreadLocal<String> CURRENT_SCENARIO = new InheritableThreadLocal<>();

  // Thread-local for test class scope (if needed)
  private static final ThreadLocal<String> CURRENT_TEST_CLASS = new InheritableThreadLocal<>();

  /**
   * Enter scenario scope (called before each scenario).
   *
   * @param scenarioId unique identifier for the scenario
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
   * Exit scenario scope (called after each scenario).
   */
  public static void exitScenario() {
    CURRENT_SCENARIO.remove();
  }

  /**
   * Enter test class scope (called at the start of test class).
   *
   * @param testClassName the test class name
   */
  public static void enterTestClass(String testClassName) {
    CURRENT_TEST_CLASS.set(SCOPE_PREFIX + testClassName);
  }

  /**
   * Get the current test class scope identifier.
   *
   * @return the current test class scope or null
   */
  public static String getCurrentTestClass() {
    return CURRENT_TEST_CLASS.get();
  }

  /**
   * Exit test class scope (called at the end of test class).
   */
  public static void exitTestClass() {
    CURRENT_TEST_CLASS.remove();
  }

  /**
   * Clear all scope context.
   */
  public static void clearAll() {
    CURRENT_SCENARIO.remove();
    CURRENT_TEST_CLASS.remove();
  }

  @Override
  public String currentScopeKey() {
    // Prefer scenario scope (most specific)
    String scenarioScope = CURRENT_SCENARIO.get();
    if (scenarioScope != null) {
      return scenarioScope;
    }

    // Fall back to test class scope
    String testClassScope = CURRENT_TEST_CLASS.get();
    if (testClassScope != null) {
      return testClassScope;
    }

    // Ultimate fallback
    return DEFAULT_SCOPE;
  }
}
