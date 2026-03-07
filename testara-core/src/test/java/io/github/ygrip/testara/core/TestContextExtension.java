package io.github.ygrip.testara.core;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.config.TestConfigurationLoader;
import io.github.ygrip.testara.core.context.DefaultTestContext;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.JUnit5ScopeContext;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.scan.ClassScannerConfig;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Log4j2
public final class TestContextExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    ExtensionContext.Store store =
        context.getStore(ExtensionContext.Namespace.create(getClass(), context.getTestClass()));

    RootRegistry registry = store.get(RootRegistry.class, RootRegistry.class);
    try {
      registry.clearScope(JUnit5ScopeContext.getClassScope(context));
      JUnit5ScopeContext.exitClass();
    } catch (Exception ignored) {
      log.warn("Fail to clear test context!");
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    ExtensionContext.Store store =
        context.getStore(ExtensionContext.Namespace.create(getClass(), context.getTestClass()));

    // Initialize class-level scope for @BeforeAll methods
    JUnit5ScopeContext.enterClass(context);

    final TestConfiguration configuration = loadConfiguration(context);

    TestContext testContext = new DefaultTestContext(configuration);
    TestFramework.initialize(testContext);

    // Bind scanner to be used globally
    ClassScannerConfig scanConfig = configuration.get(ClassScannerConfig.class);
    ClassScanner scanner = new ClassScanner(scanConfig);
    RootRegistry.instance().register(scanConfig, RegistryScope.GLOBAL);
    RootRegistry.instance().register(scanner, RegistryScope.GLOBAL);

    // Bind all configuration properties as global scope
    List<Class<?>> configurations = scanner.scan(LoadProperties.class).get(10, TimeUnit.SECONDS);
    for (Class<?> configType : configurations) {
      if (!RootRegistry.instance().hasInstance(configType)) {
        final Object instance = configuration.get(configType);
        RootRegistry.instance().register(instance, RegistryScope.GLOBAL);
      }
    }

    Map<RegistryScope, List<Class<?>>> managedComponents = new ConcurrentHashMap<>();

    // Load all test components and group it by scope
    List<Class<?>> components = scanner.scan(TestComponent.class).get(10, TimeUnit.SECONDS);
    for (Class<?> componentType : components) {
      TestComponent annotation = componentType.getAnnotation(TestComponent.class);
      RegistryScope scope = Objects.requireNonNull(annotation).scope();
      if (scope.equals(RegistryScope.THREAD)) {
        continue;
      }
      List<Class<?>> managed = managedComponents.getOrDefault(scope, new ArrayList<>());
      if (!RootRegistry.instance().hasInstance(componentType)) {
        managed.add(componentType);
      }
      managedComponents.put(scope, managed);
    }

    // Register global scope first
    managedComponents.get(RegistryScope.GLOBAL).forEach(componentType -> {
      if (!RootRegistry.instance().hasInstance(componentType, RegistryScope.GLOBAL.name())) {
        RootRegistry.instance().register(componentType, RegistryScope.GLOBAL);
      }
    });

    // Register test scope, thread scope wont be initialized
    managedComponents.get(RegistryScope.TEST).forEach(componentType -> {
      if (!RootRegistry.instance().hasInstance(componentType)) {
        RootRegistry.instance().register(componentType, RegistryScope.TEST);
      }
    });

    store.put(TestConfiguration.class, configuration);
    store.put(TestContext.class, testContext);
    store.put(RootRegistry.class, RootRegistry.instance());
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == TestContext.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return TestFramework.context();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    RootRegistry.instance().clearScope(context.getUniqueId());
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    ExtensionContext.Store store =
        context.getStore(ExtensionContext.Namespace.create(getClass(), context.getTestClass()));
    JUnit5ScopeContext.enter(context);
  }

  private TestConfiguration loadConfiguration(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    log.info("Test class {}", testClass);

    TestWith annotation = testClass.getAnnotation(TestWith.class);
    String[] propertyFiles;
    if (annotation != null) {
      // read configuration
      propertyFiles = annotation.properties();
    } else {
      propertyFiles = new String[] {"classpath:*.properties"};
    }

    System.setProperty("configuration.location", String.join(",", propertyFiles));

    return TestConfigurationLoader.load();
  }
}
