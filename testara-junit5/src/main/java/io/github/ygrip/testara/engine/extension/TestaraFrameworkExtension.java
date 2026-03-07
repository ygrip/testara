package io.github.ygrip.testara.engine.extension;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.config.TestConfigurationLoader;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestContextProvider;
import io.github.ygrip.testara.core.context.TestContextProviderLoader;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.scan.ClassScannerConfig;
import io.github.ygrip.testara.engine.context.CucumberScopeContext;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
  private static volatile TestConfiguration configuration;
  private static volatile TestContext testContext;

  @Override
  public void beforeAll(TestaraExtensionContext context) throws Exception {
    if (INITIALIZED.compareAndSet(false, true)) {
      initializeFramework(context);
    }
  }

  @Override
  public void beforeEach(TestaraExtensionContext context) throws Exception {
    // Enter scenario scope
    String scopeId = context.getUniqueId();
    CucumberScopeContext.enterScenario(scopeId);
    log.debug("Entered scenario scope: {}", scopeId);
  }

  @Override
  public void afterEach(TestaraExtensionContext context) throws Exception {
    // Exit scenario scope and clear scoped instances
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
  }

  @Override
  public void afterAll(TestaraExtensionContext context) throws Exception {
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
      TestFramework.context().get(io.github.ygrip.testara.core.function.RetryableMethodPostProcessor.class);
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
    if (!RootRegistry.instance().hasProvider(io.github.ygrip.testara.core.function.MethodInvocationCollector.class)) {
      RootRegistry.instance().register(
          io.github.ygrip.testara.core.function.MethodInvocationCollector.class,
          RegistryScope.TEST
      );
      log.debug("Explicitly registered MethodInvocationCollector for method interception");
    }

    // Register RetryableMethodPostProcessor if not already registered
    if (!RootRegistry.instance().hasProvider(io.github.ygrip.testara.core.function.RetryableMethodPostProcessor.class)) {
      RootRegistry.instance().register(
          io.github.ygrip.testara.core.function.RetryableMethodPostProcessor.class,
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
    // Check for property files configuration
    String propertyLocations = context.getConfigurationParameter("testara.configuration.location")
        .orElse("classpath:*.properties");

    System.setProperty("configuration.location", propertyLocations);

    log.debug("Loading configuration from: {}", propertyLocations);
    return TestConfigurationLoader.load();
  }

  /**
   * Register configuration properties as global scope instances.
   */
  private void registerConfigurationProperties(ClassScanner scanner, TestConfiguration config) throws Exception {
    List<Class<?>> configurations = scanner.scan(LoadProperties.class).get(10, TimeUnit.SECONDS);

    for (Class<?> configType : configurations) {
      if (!RootRegistry.instance().hasInstance(configType)) {
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

      if (!RootRegistry.instance().hasInstance(componentType)) {
        componentsByScope.get(scope).add(componentType);
      }
    }

    // Register GLOBAL scope components first
    componentsByScope.get(RegistryScope.GLOBAL).forEach(componentType -> {
      if (!RootRegistry.instance().hasInstance(componentType, RegistryScope.GLOBAL.name())) {
        RootRegistry.instance().register(componentType, RegistryScope.GLOBAL);
        log.trace("Registered GLOBAL component: {}", componentType.getSimpleName());
      }
    });

    // Register TEST scope components (they'll be instantiated per scenario)
    componentsByScope.get(RegistryScope.TEST).forEach(componentType -> {
      if (!RootRegistry.instance().hasInstance(componentType)) {
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
}
