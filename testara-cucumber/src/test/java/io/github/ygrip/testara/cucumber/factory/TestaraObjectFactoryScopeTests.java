package io.github.ygrip.testara.cucumber.factory;

import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.error.DependencyResolutionException;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.cucumber.scope.JUnit4ScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for Bug 1 (init race / fail-clearly) and Bug 2 (sequential-shares-one-run-scope vs
 * parallel-delegates-to-hooks) on the JUnit4 {@link TestaraObjectFactory} path.
 * <p>
 * {@code ObjectFactory.start()}/{@code stop()} run once per scenario under Cucumber-JVM's JUnit4
 * contract, so each scenario is simulated here with its own {@code TestaraObjectFactory}
 * instance sharing the same static framework state, exactly as real repeated scenario execution
 * would.
 */
class TestaraObjectFactoryScopeTests {

  @AfterEach
  void resetFramework() {
    TestaraObjectFactory.resetFramework();
    System.clearProperty("cucumber.execution.parallel.enabled");
    System.clearProperty("testara.configuration.location");
    System.clearProperty("configuration.location");
  }

  @Test
  void sequentialModeBindsTheSharedRunScopeOnEveryStartAndNeverClearsItOnStop() {
    System.setProperty("cucumber.execution.parallel.enabled", "false");

    TestaraObjectFactory scenario1 = new TestaraObjectFactory();
    scenario1.start();
    String scopeAfterScenario1Start = JUnit4ScopeContext.getCurrentScenario();
    assertNotNull(scopeAfterScenario1Start);
    scenario1.stop();

    // stop() must only unbind the thread-local, never clear the run scope's cached instances -
    // the next scenario rebinds the exact same key.
    TestaraObjectFactory scenario2 = new TestaraObjectFactory();
    scenario2.start();
    String scopeAfterScenario2Start = JUnit4ScopeContext.getCurrentScenario();
    scenario2.stop();

    assertEquals(scopeAfterScenario1Start, scopeAfterScenario2Start,
        "sequential scenarios must share the same run-scope key across repeated start()/stop() cycles");
  }

  @Test
  void parallelModeDoesNotBindAnyScopeInStartLeavingItToTheLifecycleHooks() {
    System.setProperty("cucumber.execution.parallel.enabled", "true");

    TestaraObjectFactory scenario = new TestaraObjectFactory();
    scenario.start();

    // start() has no access to the real Scenario/scenario.getId(), so in parallel mode it must
    // not invent a synthetic scope key itself - TestaraLifecycleHooks.beforeScenario() owns that.
    assertNull(JUnit4ScopeContext.getCurrentScenario());

    scenario.stop();
  }

  @Test
  void frameworkInitializesExactlyOnceAcrossRepeatedScenarioStartStopCycles() {
    System.setProperty("cucumber.execution.parallel.enabled", "false");

    TestaraObjectFactory scenario1 = new TestaraObjectFactory();
    scenario1.start();
    Object firstContext = TestFramework.context();
    scenario1.stop();

    TestaraObjectFactory scenario2 = new TestaraObjectFactory();
    scenario2.start();
    Object secondContext = TestFramework.context();
    scenario2.stop();

    assertEquals(firstContext, secondContext, "framework must only be initialized once for the whole run");
  }

  @Test
  void resetFrameworkAlsoClearsTheRegistrySoStaleProvidersDoNotLeakIntoTheNextRun() {
    System.setProperty("cucumber.execution.parallel.enabled", "false");

    TestaraObjectFactory scenario = new TestaraObjectFactory();
    scenario.start();
    assertTrue(RootRegistry.instance().hasProvider(io.github.ygrip.testara.core.scan.ClassScanner.class),
        "scanner should be registered as part of framework initialization");
    scenario.stop();

    TestaraObjectFactory.resetFramework();

    assertFalse(RootRegistry.instance().hasProvider(io.github.ygrip.testara.core.scan.ClassScanner.class),
        "resetFramework() must clear RootRegistry providers, not just the AtomicBoolean flag");
  }

  @Test
  void differentRunsGetADifferentRunScopeKeyAfterReset() {
    System.setProperty("cucumber.execution.parallel.enabled", "false");

    TestaraObjectFactory firstRunScenario = new TestaraObjectFactory();
    firstRunScenario.start();
    String firstRunKey = JUnit4ScopeContext.getCurrentScenario();
    firstRunScenario.stop();

    TestaraObjectFactory.resetFramework();

    TestaraObjectFactory secondRunScenario = new TestaraObjectFactory();
    secondRunScenario.start();
    String secondRunKey = JUnit4ScopeContext.getCurrentScenario();
    secondRunScenario.stop();

    assertNotEquals(firstRunKey, secondRunKey,
        "a fresh framework run must not reuse the previous run's shared scope key");
  }

  @Test
  void honorsTestaraConfigurationLocationSystemPropertyOnTheJUnit4Path() {
    System.setProperty("cucumber.execution.parallel.enabled", "false");
    System.setProperty("testara.configuration.location",
        "classpath:configuration.properties,classpath:cucumber.properties");

    TestaraObjectFactory scenario = new TestaraObjectFactory();
    scenario.start();
    scenario.stop();

    // Prior to the fix, the JUnit4 path never read testara.configuration.location at all and
    // always fell back to the default classpath:*.properties glob, silently ignoring any
    // -Dtestara.configuration.location override.
    assertEquals("classpath:configuration.properties,classpath:cucumber.properties",
        System.getProperty("configuration.location"),
        "JUnit4 initialization must honor -Dtestara.configuration.location like the JUnit5 path does");
  }

  // Deliberately NOT annotated with @TestComponent - never registered with RootRegistry.
  static class UnregisteredDependency {
  }

  @TestComponent(scope = RegistryScope.TEST)
  static class StepWithUnregisteredDependency {
    @Inject
    private UnregisteredDependency dependency;
  }

  @Test
  void getInstanceFailsFastWhenAnInjectedDependencyTypeIsNotRegistered() {
    // Regression test: createInstance() used to cascade TestFramework factory -> delegate
    // factory -> RootRegistry factory -> DefaultObjectFactory -> direct instantiation, silently
    // swallowing every exception (including a real DependencyResolutionException) down to a
    // single WARN log with no cause, and handing back a step instance whose @Inject field was
    // left null - deferring the real error into a confusing NPE the first time the field was
    // dereferenced (e.g. DataManipulationSteps.dataHolder).
    System.setProperty("cucumber.execution.parallel.enabled", "false");

    TestaraObjectFactory scenario = new TestaraObjectFactory();
    scenario.start();
    try {
      assertThrows(DependencyResolutionException.class,
          () -> scenario.getInstance(StepWithUnregisteredDependency.class));
    } finally {
      scenario.stop();
    }
  }
}
