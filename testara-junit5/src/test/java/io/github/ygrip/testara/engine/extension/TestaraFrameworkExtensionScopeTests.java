package io.github.ygrip.testara.engine.extension;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.engine.context.CucumberScopeContext;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import io.github.ygrip.testara.engine.testsupport.FakeTestDescriptor;
import io.github.ygrip.testara.engine.testsupport.MapConfigurationParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end lifecycle coverage for Bug 1 (run-level TestFramework visibility, init race) and
 * Bug 2 (sequential-shares-one-scope vs parallel-isolates-per-scenario) as implemented by
 * {@link TestaraFrameworkExtension}.
 * <p>
 * Drives the extension's real beforeAll/beforeEach/afterEach/afterAll methods directly (as the
 * Testara Cucumber JUnit5 engine would), using fake {@link org.junit.platform.engine.TestDescriptor}
 * and {@link org.junit.platform.engine.ConfigurationParameters} instances instead of a full
 * launcher session.
 */
class TestaraFrameworkExtensionScopeTests {

  private static final String PARALLEL_ENABLED = "cucumber.execution.parallel.enabled";

  @TestComponent(scope = RegistryScope.TEST)
  public static class Probe {
  }

  @AfterEach
  void resetFramework() {
    TestaraFrameworkExtension.resetFramework();
  }

  private TestaraExtensionContext context(String uniqueId, Map<String, String> configParams) {
    TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(new MapConfigurationParameters(configParams));
    return new TestaraExtensionContext(new FakeTestDescriptor(uniqueId, uniqueId), options);
  }

  @Test
  void sequentialRunSharesOneTestScopeInstanceAcrossScenarios() throws Exception {
    TestaraFrameworkExtension extension = new TestaraFrameworkExtension();
    extension.beforeAll(context("run", Map.of(PARALLEL_ENABLED, "false")));
    RootRegistry.instance().register(Probe.class, RegistryScope.TEST);

    extension.beforeEach(context("scenario-1", Map.of()));
    String keyDuringScenario1 = CucumberScopeContext.getCurrentScopeKey();
    Probe fromScenario1 = RootRegistry.instance().get(Probe.class, keyDuringScenario1);
    extension.afterEach(context("scenario-1", Map.of()));

    extension.beforeEach(context("scenario-2", Map.of()));
    String keyDuringScenario2 = CucumberScopeContext.getCurrentScopeKey();
    Probe fromScenario2 = RootRegistry.instance().get(Probe.class, keyDuringScenario2);
    extension.afterEach(context("scenario-2", Map.of()));

    assertEquals(keyDuringScenario1, keyDuringScenario2, "sequential scenarios must share one scope key");
    assertSame(fromScenario1, fromScenario2, "sequential scenarios must share one TEST-scoped instance");
  }

  @Test
  void sequentialRunScopeIsNotClearedBetweenScenariosOnlyAfterAll() throws Exception {
    TestaraFrameworkExtension extension = new TestaraFrameworkExtension();
    TestaraExtensionContext runContext = context("run", Map.of(PARALLEL_ENABLED, "false"));
    extension.beforeAll(runContext);
    RootRegistry.instance().register(Probe.class, RegistryScope.TEST);

    extension.beforeEach(context("scenario-1", Map.of()));
    String scopeKey = CucumberScopeContext.getCurrentScopeKey();
    Probe beforeAfterEach = RootRegistry.instance().get(Probe.class, scopeKey);
    extension.afterEach(context("scenario-1", Map.of()));

    // afterEach must NOT have cleared it - re-fetching the same key still returns the same
    // cached instance.
    Probe stillCached = RootRegistry.instance().get(Probe.class, scopeKey);
    assertSame(beforeAfterEach, stillCached, "run scope must survive afterEach in sequential mode");

    extension.afterAll(runContext);

    // Only afterAll actually clears it - re-fetching now creates a brand new instance.
    Probe afterRunEnded = RootRegistry.instance().get(Probe.class, scopeKey);
    assertNotSame(beforeAfterEach, afterRunEnded, "run scope must be cleared once, at afterAll");
  }

  @Test
  void parallelRunGivesEachScenarioItsOwnTestScopeInstance() throws Exception {
    TestaraFrameworkExtension extension = new TestaraFrameworkExtension();
    extension.beforeAll(context("run", Map.of(PARALLEL_ENABLED, "true")));
    RootRegistry.instance().register(Probe.class, RegistryScope.TEST);

    extension.beforeEach(context("scenario-A", Map.of()));
    String keyA = CucumberScopeContext.getCurrentScopeKey();
    Probe probeA = RootRegistry.instance().get(Probe.class, keyA);

    extension.beforeEach(context("scenario-B", Map.of()));
    String keyB = CucumberScopeContext.getCurrentScopeKey();
    Probe probeB = RootRegistry.instance().get(Probe.class, keyB);

    assertNotEquals(keyA, keyB, "concurrently running scenarios must get distinct scope keys");
    assertNotSame(probeA, probeB, "concurrently running scenarios must get isolated TEST-scoped instances");
  }

  @Test
  void parallelCleanupOfOneScenarioDoesNotClearAnotherStillActiveScenario() throws Exception {
    TestaraFrameworkExtension extension = new TestaraFrameworkExtension();
    extension.beforeAll(context("run", Map.of(PARALLEL_ENABLED, "true")));
    RootRegistry.instance().register(Probe.class, RegistryScope.TEST);

    TestaraExtensionContext scenarioA = context("scenario-A", Map.of());
    TestaraExtensionContext scenarioB = context("scenario-B", Map.of());

    extension.beforeEach(scenarioA);
    String keyA = CucumberScopeContext.getCurrentScopeKey();
    Probe probeA = RootRegistry.instance().get(Probe.class, keyA);

    extension.beforeEach(scenarioB);
    String keyB = CucumberScopeContext.getCurrentScopeKey();
    Probe probeB = RootRegistry.instance().get(Probe.class, keyB);

    // Finish scenario B while scenario A is still conceptually "active" (its scope key/instance
    // must be unaffected by B's cleanup).
    extension.afterEach(scenarioB);

    Probe probeAStillCached = RootRegistry.instance().get(Probe.class, keyA);
    assertSame(probeA, probeAStillCached, "clearing scenario B's scope must not affect scenario A's");

    Probe probeBRecreated = RootRegistry.instance().get(Probe.class, keyB);
    assertNotSame(probeB, probeBRecreated, "scenario B's scope must actually have been cleared");
  }

  @Test
  void everyScenarioWorkerThreadCanAccessTheSharedTestFrameworkContext() throws Exception {
    TestaraFrameworkExtension extension = new TestaraFrameworkExtension();
    extension.beforeAll(context("run", Map.of(PARALLEL_ENABLED, "true")));

    Object expectedContext = TestFramework.context();

    // A plain worker thread created fresh here is not a descendant of whichever thread ran
    // beforeAll in a real engine - exactly the ForkJoinPool scenario Bug 1 fixes.
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Object> observed = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      observed.set(TestFramework.context());
      latch.countDown();
    });
    worker.start();
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    assertNotNull(observed.get());
    assertSame(expectedContext, observed.get());
  }
}
