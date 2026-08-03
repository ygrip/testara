package io.github.ygrip.testara.cucumber.factory;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.config.TestConfigurationLoader;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestContextProvider;
import io.github.ygrip.testara.core.context.TestContextProviderLoader;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.error.DependencyResolutionException;
import io.github.ygrip.testara.core.factory.DefaultObjectFactory;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.core.function.MethodInvocationCollector;
import io.github.ygrip.testara.core.function.RetryableMethodPostProcessor;
import io.github.ygrip.testara.core.factory.ObjectFactoryLoader;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.scan.ClassScannerConfig;
import io.github.ygrip.testara.cucumber.scope.JUnit4ScopeContext;

/**
 * Testara ObjectFactory for JUnit 4 Cucumber runner.
 * <p>
 * This factory integrates with the testara framework when running Cucumber tests
 * with JUnit 4's {@code @RunWith(Cucumber.class)}.
 * <p>
 * <h3>Basic Usage (without Spring):</h3>
 * <pre>
 * {@code @RunWith(Cucumber.class)}
 * {@code @CucumberOptions(objectFactory = TestaraObjectFactory.class, ...)}
 * public class CucumberRunner {}
 * Features:
 * - Integrates with testara-core's RootRegistry for DI
 * - Manages scenario-scoped instances
 * - Initializes framework context on first use
 */
public class TestaraObjectFactory implements io.cucumber.core.backend.ObjectFactory {

  private static final Logger log = LoggerFactory.getLogger(TestaraObjectFactory.class);

  // Guards the one-time framework initialization race across the repeated start() calls
  // Cucumber-JVM makes (start()/stop() run once per scenario for JUnit4's ObjectFactory
  // contract). The CAS "winner" runs initializeFramework(); "losers" must wait for the winner
  // to actually finish instead of proceeding as if the framework were already ready.
  private static final AtomicBoolean FRAMEWORK_INITIALIZED = new AtomicBoolean(false);
  private static volatile CompletableFuture<Void> FRAMEWORK_READY = new CompletableFuture<>();

  // Effective execution mode + shared run-scope key, determined once when the framework
  // initializes and consulted on every start()/stop() call.
  private static volatile boolean parallelMode;
  private static volatile String runScopeKey;

  private final Map<Class<?>, Object> scenarioInstances = new ConcurrentHashMap<>();
  private ObjectFactory delegateFactory;

  /**
   * Reset the framework state.
   * Useful for testing or when restarting the framework.
   */
  public static void resetFramework() {
    FRAMEWORK_INITIALIZED.set(false);
    FRAMEWORK_READY = new CompletableFuture<>();
    parallelMode = false;
    runScopeKey = null;
    TestFramework.clear();
    RootRegistry.clearAll();
  }

  @Override
  public void start() {
    log.debug("Starting TestaraObjectFactory for JUnit 4");

    // Initialize framework on first start (once per test run)
    if (FRAMEWORK_INITIALIZED.compareAndSet(false, true)) {
      try {
        initializeFramework();
        parallelMode = JUnit4ScopeContext.isParallelExecutionEnabled();
        if (parallelMode) {
          log.debug("Parallel execution detected - TestaraLifecycleHooks owns per-scenario scope binding");
        } else {
          runScopeKey = JUnit4ScopeContext.startNewRun();
          log.debug("Sequential execution detected - sharing one TEST scope for the whole run: {}", runScopeKey);
        }
        FRAMEWORK_READY.complete(null);
      } catch (RuntimeException | Error e) {
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

    // Scope binding:
    // - Sequential: bind the shared run-scope key here. ObjectFactory.start() runs before
    //   Cucumber-JVM invokes @Before hooks, so binding eagerly here (and idempotently again in
    //   TestaraLifecycleHooks.beforeScenario()) is safe and doesn't depend on hook ordering.
    // - Parallel: do NOT generate or bind a scope key here — start() has no access to the real
    //   Scenario/scenario.getId() (ObjectFactory.start() takes no Scenario parameter), so
    //   TestaraLifecycleHooks.beforeScenario() exclusively owns per-scenario scope binding.
    if (!parallelMode) {
      JUnit4ScopeContext.enterRunScope();
    }

    // Load delegate factory via SPI
    // This will return SpringObjectFactory if Spring is available and initialized
    delegateFactory = loadBestAvailableFactory();
    delegateFactory.start();

    log.debug(
      "TestaraObjectFactory started with delegate: {}, parallelMode: {}",
      delegateFactory.getClass()
        .getSimpleName(),
      parallelMode
    );
  }

  /**
   * Load the best available ObjectFactory.
   * Checks if Spring is available and initialized before selecting SpringObjectFactory.
   */
  private ObjectFactory loadBestAvailableFactory() {
    ObjectFactory factory = ObjectFactoryLoader.load();

    // Check if the loaded factory actually supports creating instances
    // SpringObjectFactory.supports() returns false if ApplicationContext is not set
    if (factory.supports(Object.class)) {
      log.debug(
        "Using factory: {}",
        factory.getClass()
          .getSimpleName()
      );
      return factory;
    }

    // Fall back to DefaultObjectFactory if Spring is not ready
    log.debug("Primary factory not available, falling back to DefaultObjectFactory");
    return new DefaultObjectFactory();
  }

  @Override
  public void stop() {
    log.debug("Stopping TestaraObjectFactory");

    // Clear scenario-scoped instances local to this factory instance
    scenarioInstances.clear();

    // Scope instance cleanup is owned elsewhere, not here:
    // - Parallel mode: TestaraLifecycleHooks.afterScenario() already cleared the per-scenario
    //   scope (Cucumber-JVM runs @After hooks before ObjectFactory.stop()).
    // - Sequential mode: the shared run scope must survive across scenarios and is only ever
    //   cleared once, at the very end of the run - never here.
    // Only remove this thread's scope binding; the next scenario's start() rebinds as needed.
    JUnit4ScopeContext.exitScenario();

    if (delegateFactory != null) {
      delegateFactory.stop();
    }

    log.debug("TestaraObjectFactory stopped");
  }

  @Override
  public boolean addClass(Class<?> glueClass) {
    log.trace("Added glue class: {}", glueClass.getName());
    return true;
  }

  @Override
  public <T> T getInstance(Class<T> glueClass) {
    log.trace("Getting instance for: {}", glueClass.getName());

    // Check scenario-scoped cache first
    @SuppressWarnings("unchecked") T instance = (T) scenarioInstances.get(glueClass);

    if (instance == null) {
      instance = createInstance(glueClass);
      scenarioInstances.put(glueClass, instance);
    }

    return instance;
  }

  /**
   * Create an instance using the framework's ObjectFactory.
   * <p>
   * Resolution order:
   * 1. TestFramework context factory (if initialized)
   * 2. Delegate factory (loaded via SPI - may be SpringObjectFactory)
   * 3. RootRegistry factory
   * 4. Direct instantiation (fallback)
   */
  private <T> T createInstance(Class<T> type) {
    // Try TestFramework context first
    if (isFrameworkInitialized()) {
      try {
        return TestFramework.context()
          .factory()
          .getInstance(type);
      } catch (DependencyResolutionException e) {
        // A genuine resolution failure (e.g. the type's owning package isn't covered by
        // class.loader.default-scan-locations, or a required dependency was never registered)
        // is a real configuration error - not something a different factory implementation
        // (Spring delegate, RootRegistry, DefaultObjectFactory) would fix, and never something
        // silent direct instantiation should paper over. Propagate immediately instead of
        // cascading through fallbacks that would just mask it and hand back an uninjected,
        // silently-broken instance (see class javadoc / the WARN below for the symptom this
        // used to produce: "All factory methods failed ... using direct instantiation" with no
        // indication of why).
        throw e;
      } catch (Exception e) {
        log.debug("TestFramework factory failed for {}: {}", type.getName(), e.getMessage());
        // Continue to next option
      }
    }

    // Try delegate factory (check if it supports the type first)
    if (delegateFactory != null && delegateFactory.supports(type)) {
      try {
        return delegateFactory.getInstance(type);
      } catch (Exception e) {
        log.debug("Delegate factory failed for {}: {}", type.getName(), e.getMessage());
        // Continue to next option
      }
    }

    // Try RootRegistry factory
    try {
      ObjectFactory registryFactory = RootRegistry.instance()
        .factory();
      if (registryFactory.supports(type)) {
        return registryFactory.getInstance(type);
      }
    } catch (DependencyResolutionException e) {
      // Same reasoning as the TestFramework factory branch above: a real configuration error,
      // not something DefaultObjectFactory/direct instantiation should silently paper over.
      throw e;
    } catch (Exception e) {
      log.debug("RootRegistry factory failed for {}: {}", type.getName(), e.getMessage());
    }

    // Fall back to DefaultObjectFactory (always works)
    try {
      return new DefaultObjectFactory().getInstance(type);
    } catch (Exception e) {
      log.debug("DefaultObjectFactory failed for {}: {}", type.getName(), e.getMessage());
    }

    // Last resort: direct instantiation
    log.warn("All factory methods failed for {}, using direct instantiation", type.getName());
    try {
      return type.getDeclaredConstructor()
        .newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Failed to create instance of " + type.getName(), ex);
    }
  }

  /**
   * Initialize the testara framework (once per test run).
   */
  private void initializeFramework() {
    log.debug("Initializing Testara Framework for JUnit 4...");

    try {
      // Load configuration
      TestConfigurationLoader.resolveConfigurationLocation(null);
      TestConfiguration configuration = TestConfigurationLoader.load();

      // Create TestContext via SPI
      TestContextProvider provider = TestContextProviderLoader.load();
      log.debug(
        "Using TestContextProvider: {}",
        provider.getClass()
          .getSimpleName()
      );

      String runId = "junit4-run-" + System.currentTimeMillis();
      TestContext testContext = provider.create(configuration, runId);

      // Initialize TestFramework
      TestFramework.initialize(testContext);

      // Register scanner
      ClassScannerConfig scanConfig = configuration.get(ClassScannerConfig.class);
      ClassScanner scanner = new ClassScanner(scanConfig);
      RootRegistry.instance()
        .register(scanConfig, RegistryScope.GLOBAL);
      RootRegistry.instance()
        .register(scanner, RegistryScope.GLOBAL);

      // Register configuration properties
      registerConfigurationProperties(scanner, configuration);

      // Register test components
      registerTestComponents(scanner);

      // Enable method interception EARLY - before any step classes are created
      // This ensures @RetryableMethod proxies are created when step classes are instantiated
      enableMethodInterception();

      log.debug("Testara Framework initialized for JUnit 4");

    } catch (Exception e) {
      // Requirement: do not silently swallow framework initialization failures and continue
      // using fallback object creation - fail clearly so start()'s caller (and any other
      // scenario waiting on FRAMEWORK_READY) sees the failure instead of a half-initialized,
      // silently-reset framework.
      log.error("Failed to initialize Testara Framework: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to initialize Testara Framework for JUnit 4", e);
    }
  }

  private void registerConfigurationProperties(ClassScanner scanner, TestConfiguration config) throws Exception {
    List<Class<?>> configurations = scanner.scan(LoadProperties.class)
      .get(10, TimeUnit.SECONDS);

    for (Class<?> configType : configurations) {
      if (!RootRegistry.instance()
        .hasProvider(configType)) {
        Object instance = config.get(configType);
        RootRegistry.instance()
          .register(instance, RegistryScope.GLOBAL);
        log.trace("Registered configuration: {}", configType.getSimpleName());
      }
    }
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
      TestFramework.context()
        .get(RetryableMethodPostProcessor.class);
      log.trace("Method interception enabled via RetryableMethodPostProcessor");
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
    if (!RootRegistry.instance()
      .hasProvider(MethodInvocationCollector.class)) {
      RootRegistry.instance()
        .register(MethodInvocationCollector.class, RegistryScope.TEST);
      log.debug("Explicitly registered MethodInvocationCollector for method interception");
    }

    // Register RetryableMethodPostProcessor if not already registered
    if (!RootRegistry.instance()
      .hasProvider(RetryableMethodPostProcessor.class)) {
      RootRegistry.instance()
        .register(RetryableMethodPostProcessor.class, RegistryScope.TEST);
      log.debug("Explicitly registered RetryableMethodPostProcessor for method interception");
    }
  }

  private void registerTestComponents(ClassScanner scanner) throws Exception {
    Map<RegistryScope, List<Class<?>>> componentsByScope = new ConcurrentHashMap<>();

    for (RegistryScope scope : RegistryScope.values()) {
      componentsByScope.put(scope, new ArrayList<>());
    }

    List<Class<?>> components = scanner.scan(TestComponent.class)
      .get(10, TimeUnit.SECONDS);

    for (Class<?> componentType : components) {
      TestComponent annotation = componentType.getAnnotation(TestComponent.class);
      RegistryScope scope = Objects.requireNonNull(annotation)
        .scope();

      if (scope == RegistryScope.THREAD) {
        continue;
      }

      if (!RootRegistry.instance()
        .hasProvider(componentType)) {
        componentsByScope.get(scope)
          .add(componentType);
      }
    }

    // Register GLOBAL scope first
    componentsByScope.get(RegistryScope.GLOBAL)
      .forEach(componentType -> {
        if (!RootRegistry.instance()
          .hasProvider(componentType)) {
          RootRegistry.instance()
            .register(componentType, RegistryScope.GLOBAL);
          log.trace("Registered GLOBAL component: {}", componentType.getSimpleName());
        }
      });

    // Register TEST scope
    componentsByScope.get(RegistryScope.TEST)
      .forEach(componentType -> {
        if (!RootRegistry.instance()
          .hasProvider(componentType)) {
          RootRegistry.instance()
            .register(componentType, RegistryScope.TEST);
          log.trace("Registered TEST component: {}", componentType.getSimpleName());
        }
      });
  }

  private boolean isFrameworkInitialized() {
    try {
      TestFramework.context();
      return true;
    } catch (IllegalStateException e) {
      return false;
    }
  }
}
