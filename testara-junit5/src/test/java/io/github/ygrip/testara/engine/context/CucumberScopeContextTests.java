package io.github.ygrip.testara.engine.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for Bug 2's run-scope vs scenario-scope key distinction in
 * {@link CucumberScopeContext}.
 */
class CucumberScopeContextTests {

  @AfterEach
  void cleanup() {
    CucumberScopeContext.exitScenario();
    CucumberScopeContext.exitFeature();
  }

  @Test
  void enterRunScopeBindsToTheSharedRunScopeKey() {
    String runKey = CucumberScopeContext.startNewRun();

    CucumberScopeContext.enterRunScope();

    assertEquals(runKey, CucumberScopeContext.getCurrentScopeKey());
  }

  @Test
  void enterRunScopeIsIdempotentAcrossMultipleScenarios() {
    String runKey = CucumberScopeContext.startNewRun();

    CucumberScopeContext.enterRunScope();
    String firstScenarioKey = CucumberScopeContext.getCurrentScopeKey();
    CucumberScopeContext.enterRunScope();
    String secondScenarioKey = CucumberScopeContext.getCurrentScopeKey();

    assertEquals(runKey, firstScenarioKey);
    assertEquals(runKey, secondScenarioKey);
  }

  @Test
  void startNewRunGeneratesADifferentKeyEachTime() {
    String first = CucumberScopeContext.startNewRun();
    String second = CucumberScopeContext.startNewRun();

    assertNotEquals(first, second);
  }

  @Test
  void enterScenarioIsIndependentOfRunScope() {
    String runKey = CucumberScopeContext.startNewRun();

    CucumberScopeContext.enterScenario("scenario-42");

    assertNotEquals(runKey, CucumberScopeContext.getCurrentScopeKey());
    assertTrue(CucumberScopeContext.getCurrentScopeKey().endsWith("scenario-42"));
  }
}
