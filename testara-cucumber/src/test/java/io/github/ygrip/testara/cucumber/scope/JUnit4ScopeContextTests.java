package io.github.ygrip.testara.cucumber.scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for Bug 2's run-scope vs scenario-scope key distinction on the JUnit4 path, and
 * for the parallel-mode detection {@link TestaraObjectFactory}/{@code TestaraLifecycleHooks}
 * both rely on.
 */
class JUnit4ScopeContextTests {

  @AfterEach
  void cleanup() {
    JUnit4ScopeContext.exitScenario();
    JUnit4ScopeContext.exitTestClass();
    System.clearProperty("cucumber.execution.parallel.enabled");
  }

  @Test
  void enterRunScopeBindsToTheSharedRunScopeKey() {
    String runKey = JUnit4ScopeContext.startNewRun();

    JUnit4ScopeContext.enterRunScope();

    assertEquals(runKey, JUnit4ScopeContext.getCurrentScenario());
    assertEquals(runKey, new JUnit4ScopeContext().currentScopeKey());
  }

  @Test
  void enterRunScopeIsIdempotentAcrossMultipleScenarios() {
    String runKey = JUnit4ScopeContext.startNewRun();

    JUnit4ScopeContext.enterRunScope();
    String firstScenarioKey = JUnit4ScopeContext.getCurrentScenario();
    JUnit4ScopeContext.enterRunScope();
    String secondScenarioKey = JUnit4ScopeContext.getCurrentScenario();

    assertEquals(runKey, firstScenarioKey);
    assertEquals(runKey, secondScenarioKey);
  }

  @Test
  void startNewRunGeneratesADifferentKeyEachTime() {
    String first = JUnit4ScopeContext.startNewRun();
    String second = JUnit4ScopeContext.startNewRun();

    assertNotEquals(first, second);
  }

  @Test
  void enterScenarioIsIndependentOfRunScope() {
    String runKey = JUnit4ScopeContext.startNewRun();

    JUnit4ScopeContext.enterScenario("scenario-42");

    assertNotEquals(runKey, JUnit4ScopeContext.getCurrentScenario());
    assertEquals("scenario-42", JUnit4ScopeContext.getCurrentScenario());
  }

  @Test
  void parallelExecutionEnabledReadsSystemProperty() {
    System.setProperty("cucumber.execution.parallel.enabled", "true");
    assertTrue(JUnit4ScopeContext.isParallelExecutionEnabled());

    System.setProperty("cucumber.execution.parallel.enabled", "false");
    assertFalse(JUnit4ScopeContext.isParallelExecutionEnabled());
  }

  @Test
  void parallelExecutionDefaultsToFalseWhenUnset() {
    System.clearProperty("cucumber.execution.parallel.enabled");
    assertFalse(JUnit4ScopeContext.isParallelExecutionEnabled());
  }
}
