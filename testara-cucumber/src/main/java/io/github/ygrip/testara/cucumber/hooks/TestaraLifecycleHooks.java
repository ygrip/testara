package io.github.ygrip.testara.cucumber.hooks;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.cucumber.scope.JUnit4ScopeContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Cucumber hooks for testara framework lifecycle management.
 * <p>
 * These hooks integrate the testara framework with Cucumber's lifecycle:
 * - Before: Set up scenario scope for test isolation
 * - After: Clean up scenario scope and resources
 * <p>
 * Add this class to your glue path to enable framework integration:
 * {@code glue = {"io.github.ygrip.testara.cucumber.hooks", ...}}
 * <p>
 * Note: These hooks work with both JUnit 4 and JUnit 5 Cucumber runners.
 * The hooks have low order values to run first/last in the lifecycle.
 */
public class TestaraLifecycleHooks {

  private static final Logger log = LoggerFactory.getLogger(TestaraLifecycleHooks.class);

  // MDC keys for logging context
  private static final String MDC_SCENARIO = "scenario";
  private static final String MDC_SCENARIO_ID = "scenarioId";
  private static final String MDC_FEATURE = "feature";

  /**
   * Before hook - runs before each scenario.
   * Sets up scenario scope for instance isolation and MDC logging context.
   *
   * @param scenario the current scenario
   */
  @Before(order = Integer.MIN_VALUE + 1)
  public void beforeScenario(Scenario scenario) {
    String scenarioId = scenario.getId();
    String scenarioName = scenario.getName();
    String featureName = extractFeatureName(scenario.getUri().toString());
    String shortId = generateShortId(scenarioId);

    // Set MDC values for logging context
    MDC.put(MDC_SCENARIO, truncate(scenarioName, 50));
    MDC.put(MDC_SCENARIO_ID, shortId);
    MDC.put(MDC_FEATURE, featureName);

    log.debug("Before scenario: {} ({})", scenarioName, shortId);

    // Enter scenario scope. Sequential runs share one TEST scope for the whole run;
    // parallel runs isolate each concurrently running scenario with its own scope key.
    if (JUnit4ScopeContext.isParallelExecutionEnabled()) {
      JUnit4ScopeContext.enterScenario(scenarioId);
      log.debug("Entered per-scenario scope (parallel): {}", scenarioId);
    } else {
      JUnit4ScopeContext.enterRunScope();
      log.debug("Bound to shared run scope (sequential): {}", JUnit4ScopeContext.runScopeKey());
    }

    log.info("""
      
      ═══════════════════════════════════════════════════════════════
      ▶ SCENARIO START [{}] [{}]
        Name   : {}
      ═══════════════════════════════════════════════════════════════
      """, shortId, featureName, scenarioName);
  }

  /**
   * After hook - runs after each scenario.
   * Cleans up scenario scope, resources, and MDC logging context.
   *
   * @param scenario the current scenario
   */
  @After(order = Integer.MAX_VALUE - 1)
  public void afterScenario(Scenario scenario) {
    String scenarioId = scenario.getId();
    String featureName = extractFeatureName(scenario.getUri().toString());
    String shortId = generateShortId(scenarioId);

    log.info("""
      
      ═══════════════════════════════════════════════════════════════
      ◀ SCENARIO END [{}] [{}]
        Name   : {}
        Status : {}
      ═══════════════════════════════════════════════════════════════
      """, shortId, featureName, scenario.getName(), scenario.getStatus());

    if (JUnit4ScopeContext.isParallelExecutionEnabled()) {
      try {
        // Clear this scenario's isolated scope from registry
        String currentScope = JUnit4ScopeContext.getCurrentScenario();
        if (currentScope != null) {
          RootRegistry.instance().clearScope(currentScope);
          log.trace("Cleared scenario scope: {}", currentScope);
        }
      } catch (Exception e) {
        log.warn("Failed to clear scenario scope: {}", e.getMessage());
      } finally {
        // Exit scenario scope so the ThreadLocal doesn't leak into whatever scenario this
        // pooled worker thread runs next.
        JUnit4ScopeContext.exitScenario();
      }
    } else {
      // Sequential: the shared run scope must survive across scenarios - never clear it here.
      log.trace("Sequential scenario finished; retaining shared run scope: {}", JUnit4ScopeContext.runScopeKey());
    }

    // Clear MDC logging context
    MDC.remove(MDC_SCENARIO);
    MDC.remove(MDC_SCENARIO_ID);
    MDC.remove(MDC_FEATURE);
  }

  /**
   * Get the current TestContext if available.
   * Useful for accessing framework services in step definitions.
   *
   * @return the current TestContext or null if not initialized
   */
  public static Object getTestContext() {
    try {
      return TestFramework.context();
    } catch (IllegalStateException e) {
      log.debug("TestContext not available: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Generate a short unique ID from the full scenario ID.
   * Uses first 8 chars of hash for brevity while maintaining uniqueness.
   */
  private String generateShortId(String scenarioId) {
    if (scenarioId == null || scenarioId.isEmpty()) {
      return "unknown";
    }
    int hash = scenarioId.hashCode();
    return String.format("%08x", hash).substring(0, 8);
  }

  /**
   * Extract feature name from URI.
   */
  private String extractFeatureName(String uri) {
    if (uri == null) {
      return "unknown";
    }

    try {
      // Extract just the filename without path and extension
      int lastSlash = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('\\'));
      String filename = lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;

      // Remove .feature extension
      if (filename.endsWith(".feature")) {
        filename = filename.substring(0, filename.length() - 8);
      }
      return filename;
    } catch (Exception e) {
      return "unknown";
    }
  }

  /**
   * Truncate string to max length, adding "..." if truncated.
   */
  private String truncate(String str, int maxLength) {
    if (str == null) {
      return "unknown";
    }
    if (str.length() <= maxLength) {
      return str;
    }
    return str.substring(0, maxLength - 3) + "...";
  }
}
