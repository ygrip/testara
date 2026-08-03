package io.github.ygrip.testara.engine.extension;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.config.TestConfigurationLoader;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestContextProvider;
import io.github.ygrip.testara.core.context.TestContextProviderLoader;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.function.MethodInvocationCollector;
import io.github.ygrip.testara.core.function.RetryableMethodPostProcessor;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.scan.ClassScannerConfig;
import io.github.ygrip.testara.core.context.ResourceShutdownRegistry;
import io.github.ygrip.testara.engine.context.CucumberScopeContext;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TestaraExtension that initializes the framework TestContext and manages lifecycle.
 * <p>
 * This extension is agnostic about which TestContext implementation is used.
 * It delegates to TestContextProvider SPI to create the appropriate context:
 * - Default: uses testara-core's DefaultTestContext
 * - With Spring: uses testara-spring's SpringTestContext (when testara-spring is on classpath)
 * <p>
 * Responsibilities:
 * - Load configuration via SPI
 * - Create TestContext via SPI (TestContextProvider)
 * - Initialize TestFramework with the context
 * - Register components with appropriate scopes
 * - Manage scope lifecycle for Cucumber scenarios
 */
@Log4j2
public class TestaraFrameworkExtension implements TestaraExtension {

  // Guards the one-time framework initialization race. The CAS "winner" runs
  // initializeFramework(); "losers" (concurrent beforeAll invocations, if any) must wait for the
  // winner to actually finish rather than proceeding as if the framework were already ready.
  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
  private static volatile CompletableFuture<Void> FRAMEWORK_READY = new CompletableFuture<>();
  private static volatile TestConfiguration configuration;
  private static volatile TestContext testContext;

  // Effective execution mode + the shared run-scope key, determined once in beforeAll and used
  // by beforeEach/afterEach/afterAll to pick the correct scope-binding strategy: one shared TEST
  // scope for the whole run when sequential, one isolated TEST scope per scenario when parallel.
  private static volatile boolean parallelMode;
  private static volatile String runScopeKey;

  @Override
  public void beforeAll(TestaraExtensionContext context) throws Exception {
    if (INITIALIZED.compareAndSet(false, true)) {
      try {
        initializeFramework(context);
        parallelMode = isParallelExecutionEnabled(context);
        if (parallelMode) {
          log.debug("Parallel execution detected - each scenario will get its own TEST scope key");
        } else {
          runScopeKey = CucumberScopeContext.startNewRun();
          log.debug("Sequential execution detected - sharing one TEST scope for the whole run: {}", runScopeKey);
        }
        FRAMEWORK_READY.complete(null);
      } catch (Exception e) {
        FRAMEWORK_READY.completeExceptionally(e);
        throw e;
      } catch (Error e) {
        FRAMEWORK_READY.completeExceptionally(e);
        throw e;
      }
    } else {
      try {
        FRAMEWORK_READY.get(60, TimeUnit.SECONDS);
      } catch (ExecutionException ee) {
        throw new IllegalStateException("Testara framework initialization failed", ee.getCause());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted waiting for Testara framework initialization", e);
      } catch (TimeoutException e) {
        throw new IllegalStateException("Timed out waiting for Testara framework initialization", e);
      }
    }
  }

  /**
   * Determine whether parallel execution is enabled, reading from the same merged
   * {@code ConfigurationParameters} the Testara Cucumber engine itself uses
   * (see {@code TestaraCucumberEngineOptions.isParallelExecutionEnabled()}) — the extension's
   * {@link TestaraExtensionContext#getConfigurationParameter(String)} delegates to the exact
   * same per-run {@code TestaraCucumberEngineOptions} instance the engine constructed, so both
   * agree on parallel-mode by construction.
   */
  private boolean isParallelExecutionEnabled(TestaraExtensionContext context) {
    return context.getConfigurationParameter("cucumber.execution.parallel.enabled")
        .map(Boolean::parseBoolean)
        .orElse(false);
  }

  @Override
  public void beforeEach(TestaraExtensionContext context) throws Exception {
    if (parallelMode) {
      // Parallel: isolate each concurrently running scenario with its own TEST scope key.
      String scopeId = context.getUniqueId();
      CucumberScopeContext.enterScenario(scopeId);
      log.debug("Entered scenario scope (parallel): {}", scopeId);
    } else {
      // Sequential: bind (idempotently) to the single TEST scope shared by the whole run.
      CucumberScopeContext.enterRunScope();
      log.debug("Bound to shared run scope (sequential): {}", runScopeKey);
    }
  }

  @Override
  public void afterEach(TestaraExtensionContext context) throws Exception {
    if (parallelMode) {
      // Parallel: this scenario is done - clear its isolated instances and unbind immediately so
      // the ThreadLocal doesn't leak into whatever scenario the pooled worker thread runs next.
      String scopeId = context.getUniqueId();
      try {
        // Use getCurrentScopeKey() which includes the prefix, matching the cache key
        // This ensures the correct scope is cleared after each scenario
        String scopeKey = CucumberScopeContext.getCurrentScopeKey();
        RootRegistry.instance().clearScope(scopeKey);
        log.debug("Cleared scope: {}", scopeKey);
      } catch (Exception e) {
        log.warn("Failed to clear scenario scope: {}", e.getMessage());
      } finally {
        CucumberScopeContext.exitScenario();
        log.debug("Exited scenario scope: {}", scopeId);
      }
    } else {
      // Sequential: never clear the shared run scope between scenarios - it (and its TEST-scoped
      // instances, e.g. DataHolder/PageFinder) must survive for the entire run. Leave the
      // ThreadLocal binding in place; the next scenario's beforeEach rebinds the same key anyway.
      log.debug("Sequential scenario finished; retaining shared run scope: {}", runScopeKey);
    }
  }

  @Override
  public void afterAll(TestaraExtensionContext context) throws Exception {
    // Run all registered resource shutdown callbacks (proxies, clients, etc.)
    try {
      ResourceShutdownRegistry.shutdownAll();
    } catch (Exception e) {
      log.warn("Error during resource shutdown: {}", e.getMessage());
    }

    // Sequential mode: the shared run scope is only ever cleared here, once, at the end of the
    // whole run - never in between scenarios.
    if (!parallelMode && runScopeKey != null) {
      try {
        RootRegistry.instance().clearScope(runScopeKey);
        log.debug("Cleared run scope at end of run: {}", runScopeKey);
      } catch (Exception e) {
        log.warn("Failed to clear run scope: {}", e.getMessage());
      } finally {
        CucumberScopeContext.exitScenario();
      }
    }

    // Clean up feature-level scope if set
    String featureScope = CucumberScopeContext.getCurrentFeature();
    if (featureScope != null) {
      try {
        RootRegistry.instance().clearScope(featureScope);
      } catch (Exception e) {
        log.warn("Failed to clear feature scope: {}", e.getMessage());
      } finally {
        CucumberScopeContext.exitFeature();
      }
    }
    log.debug("Framework cleanup completed for context: {}", context.getUniqueId());
  }

  /**
   * Initialize the framework with TestContext loaded via SPI.
   * This method is idempotent - only initializes once per test run.
   */
  private void initializeFramework(TestaraExtensionContext context) throws Exception {
    log.info("Initializing Testara Framework...");

    // Load configuration via SPI
    configuration = loadConfiguration(context);

    // Create TestContext via SPI (provider determines implementation)
    TestContextProvider provider = TestContextProviderLoader.load();
    log.debug("Using TestContextProvider: {}", provider.getClass().getSimpleName());

    testContext = provider.create(configuration, context.getUniqueId());
    log.trace("TestContext created: {}", testContext.getClass().getSimpleName());

    // Initialize TestFramework
    TestFramework.initialize(testContext);

    // Register scanner for component discovery
    ClassScannerConfig scanConfig = configuration.get(ClassScannerConfig.class);
    ClassScanner scanner = new ClassScanner(scanConfig);
    RootRegistry.instance().register(scanConfig, RegistryScope.GLOBAL);
    RootRegistry.instance().register(scanner, RegistryScope.GLOBAL);

    // Load and register configuration properties as global scope
    registerConfigurationProperties(scanner, configuration);

    // Load and register test components by scope
    registerTestComponents(scanner);

    // Enable method interception EARLY - before any step classes are created
    // This ensures @RetryableMethod proxies are created when step classes are instantiated
    enableMethodInterception();

    log.trace("Testara Framework initialized successfully");
  }

  /**
   * Enable method interception by instantiating RetryableMethodPostProcessor early.
   * This MUST be called before any step classes with @RetryableMethod are created,
   * otherwise they won't be proxied and methods will execute immediately instead of being collected.
   */
  private void enableMethodInterception() {
    try {
      // First, ensure the required components are registered in RootRegistry
      // These might not be discovered by ClassScanner if the scan locations don't include
      // io.github.ygrip.testara.core.function package
      ensureRetryableComponentsRegistered();
      
      // Now instantiate RetryableMethodPostProcessor - this registers MethodInterceptionPostProcessor
      // with PostProcessorRegistry, enabling ByteBuddy proxy creation for @RetryableMethod classes
      TestFramework.context().get(RetryableMethodPostProcessor.class);
    } catch (Exception e) {
      log.warn("Failed to enable method interception: {}. @RetryableMethod won't work.", e.getMessage());
    }
  }

  /**
   * Ensure the retryable method components are registered in RootRegistry.
   * These components are essential for @RetryableMethod to work but might not be
   * discovered by ClassScanner if the scan locations don't include the framework packages.
   */
  private void ensureRetryableComponentsRegistered() {
    // Register MethodInvocationCollector if not already registered (dependency of RetryableMethodPostProcessor)
    if (!RootRegistry.instance().hasProvider(MethodInvocationCollector.class)) {
      RootRegistry.instance().register(
          MethodInvocationCollector.class,
          RegistryScope.TEST
      );
      log.debug("Explicitly registered MethodInvocationCollector for method interception");
    }

    // Register RetryableMethodPostProcessor if not already registered
    if (!RootRegistry.instance().hasProvider(RetryableMethodPostProcessor.class)) {
      RootRegistry.instance().register(
          RetryableMethodPostProcessor.class,
          RegistryScope.TEST
      );
      log.debug("Explicitly registered RetryableMethodPostProcessor for method interception");
    }
  }

  /**
   * Load configuration from property files.
   * Uses configuration parameters from the test engine.
   */
  private TestConfiguration loadConfiguration(TestaraExtensionContext context) {
    TestConfigurationLoader.resolveConfigurationLocation(
        context.getConfigurationParameter("testara.configuration.location").orElse(null));
    return TestConfigurationLoader.load();
  }

  /**
   * Register configuration properties as global scope instances.
   */
  private void registerConfigurationProperties(ClassScanner scanner, TestConfiguration config) throws Exception {
    List<Class<?>> configurations = scanner.scan(LoadProperties.class).get(10, TimeUnit.SECONDS);

    for (Class<?> configType : configurations) {
      if (!RootRegistry.instance().hasProvider(configType)) {
        Object instance = config.get(configType);
        RootRegistry.instance().register(instance, RegistryScope.GLOBAL);
        log.trace("Registered configuration: {}", configType.getSimpleName());
      }
    }
  }

  /**
   * Register test components with their declared scopes.
   */
  private void registerTestComponents(ClassScanner scanner) throws Exception {
    Map<RegistryScope, List<Class<?>>> componentsByScope = new ConcurrentHashMap<>();

    // Initialize scope lists
    for (RegistryScope scope : RegistryScope.values()) {
      componentsByScope.put(scope, new ArrayList<>());
    }

    // Discover and categorize components
    List<Class<?>> components = scanner.scan(TestComponent.class).get(10, TimeUnit.SECONDS);

    for (Class<?> componentType : components) {
      TestComponent annotation = componentType.getAnnotation(TestComponent.class);
      RegistryScope scope = Objects.requireNonNull(annotation).scope();

      // Skip THREAD scope components - they're created per thread lazily
      if (scope == RegistryScope.THREAD) {
        continue;
      }

      if (!RootRegistry.instance().hasProvider(componentType)) {
        componentsByScope.get(scope).add(componentType);
      }
    }

    // Register GLOBAL scope components first
    componentsByScope.get(RegistryScope.GLOBAL).forEach(componentType -> {
      if (!RootRegistry.instance().hasProvider(componentType)) {
        RootRegistry.instance().register(componentType, RegistryScope.GLOBAL);
        log.trace("Registered GLOBAL component: {}", componentType.getSimpleName());
      }
    });

    // Register TEST scope components (they'll be instantiated per scenario)
    componentsByScope.get(RegistryScope.TEST).forEach(componentType -> {
      if (!RootRegistry.instance().hasProvider(componentType)) {
        RootRegistry.instance().register(componentType, RegistryScope.TEST);
        log.trace("Registered TEST component: {}", componentType.getSimpleName());
      }
    });
  }

  /**
   * Get the current TestConfiguration.
   * Useful for extensions that need access to configuration.
   */
  public static TestConfiguration getConfiguration() {
    return configuration;
  }

  /**
   * Get the current TestContext.
   * Useful for extensions that need direct context access.
   */
  public static TestContext getTestContext() {
    return testContext;
  }

  /**
   * Check if the framework has been initialized.
   */
  public static boolean isInitialized() {
    return INITIALIZED.get();
  }

  /**
   * Reset all static framework state. Intended for tests only — allows simulating multiple
   * independent engine runs (e.g. one sequential, one parallel) within the same JVM, since
   * a completed {@link CompletableFuture} cannot be "un-completed" and must be replaced.
   */
  public static void resetFramework() {
    INITIALIZED.set(false);
    FRAMEWORK_READY = new CompletableFuture<>();
    parallelMode = false;
    runScopeKey = null;
    configuration = null;
    testContext = null;
    TestFramework.clear();
    RootRegistry.clearAll();
  }
}
