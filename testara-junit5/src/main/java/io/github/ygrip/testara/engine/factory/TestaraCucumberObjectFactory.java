package io.github.ygrip.testara.engine.factory;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.core.factory.ObjectFactoryLoader;
import io.github.ygrip.testara.core.registry.RootRegistry;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cucumber ObjectFactory implementation that bridges to the testara framework.
 * <p>
 * This factory delegates object creation to the framework's ObjectFactory SPI,
 * which means:
 * - By default: uses testara-core's DefaultObjectFactory
 * - With testara-spring: uses SpringObjectFactory for Spring DI support
 * <p>
 * Registered via SPI at META-INF/services/io.cucumber.core.backend.ObjectFactory
 */
@Log4j2
public class TestaraCucumberObjectFactory implements io.cucumber.core.backend.ObjectFactory {

  private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();
  private volatile ObjectFactory delegateFactory;

  @Override
  public void start() {
    log.trace("Starting TestaraCucumberObjectFactory");
    
    // Load the delegate factory via SPI
    // This will be SpringObjectFactory if testara-spring is on classpath
    delegateFactory = ObjectFactoryLoader.load();
    delegateFactory.start();
    
    log.trace("TestaraCucumberObjectFactory started with delegate: {}",
        delegateFactory.getClass().getSimpleName());
  }

  @Override
  public void stop() {
    log.trace("Stopping TestaraCucumberObjectFactory");
    
    // Clear scenario-scoped instances
    instances.clear();
    
    if (delegateFactory != null) {
      delegateFactory.stop();
    }
    
    log.trace("TestaraCucumberObjectFactory stopped");
  }

  @Override
  public boolean addClass(Class<?> glueClass) {
    // Accept all glue classes - we'll create them on demand
    log.trace("Added glue class: {}", glueClass.getName());
    return true;
  }

  @Override
  public <T> T getInstance(Class<T> glueClass) {
    log.trace("Getting instance for: {}", glueClass.getName());
    
    // Check if we already have an instance for this scenario
    @SuppressWarnings("unchecked")
    T instance = (T) instances.get(glueClass);
    
    if (instance == null) {
      instance = createInstance(glueClass);
      instances.put(glueClass, instance);
    }
    
    return instance;
  }

  /**
   * Create an instance using the framework's ObjectFactory.
   * Falls back to direct instantiation only when the framework is not yet initialized.
   */
  private <T> T createInstance(Class<T> type) {
    if (isFrameworkInitialized()) {
      // The framework is up - a resolution failure here (e.g. a missing scan-location
      // surfacing as DependencyResolutionException) is a real configuration error, not a
      // bootstrap-timing issue. Let it propagate: silently falling back to plain reflection
      // would bypass every InstancePostProcessor (including @Inject field injection) and hand
      // the caller a step instance whose @Inject fields are silently null, deferring the real
      // error into a confusing NPE the first time the field is used.
      return TestFramework.context().factory().getInstance(type);
    }

    try {
      // Try delegate factory
      if (delegateFactory != null) {
        return delegateFactory.getInstance(type);
      }

      // Fallback to RootRegistry factory
      return RootRegistry.instance().factory().getInstance(type);

    } catch (Exception e) {
      log.debug("Framework creation failed for {} before the framework was initialized, using direct instantiation: {}",
          type.getName(), e.getMessage());

      // Last resort: direct instantiation
      try {
        return type.getDeclaredConstructor().newInstance();
      } catch (Exception ex) {
        throw new RuntimeException("Failed to create instance of " + type.getName(), ex);
      }
    }
  }

  /**
   * Check if the TestFramework has been initialized.
   */
  private boolean isFrameworkInitialized() {
    try {
      TestFramework.context();
      return true;
    } catch (IllegalStateException e) {
      return false;
    }
  }
}
