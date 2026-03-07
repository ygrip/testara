package io.github.ygrip.testara.spring.context;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.spring.config.SpringConfiguration;
import io.github.ygrip.testara.spring.factory.SpringObjectFactory;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.util.UUID;

/**
 * Spring-backed TestContext implementation.
 * <p>
 * This context bridges testara-core's TestContext with Spring's ApplicationContext:
 * - Uses Spring for bean resolution when available
 * - Falls back to testara-core's registry for non-Spring beans
 * - Supports scope-based instance isolation (GLOBAL, THREAD, TEST)
 * <p>
 * When Spring is present, beans discovered by Spring are available via this context,
 * and instances registered in testara-core can also be resolved.
 */
@Log4j2
public class SpringTestContext implements TestContext {

  private final ObjectFactory factory;
  private final TestConfiguration configuration;
  private final ObjectConverter converter;
  private final String scopeId;

  /**
   * Create a SpringTestContext with auto-generated scope ID.
   */
  public SpringTestContext() {
    this(new SpringConfiguration());
  }

  /**
   * Create a SpringTestContext with the given configuration.
   */
  public SpringTestContext(TestConfiguration configuration) {
    this(configuration, UUID.randomUUID().toString());
  }

  /**
   * Create a SpringTestContext with specific configuration and scope ID.
   */
  public SpringTestContext(TestConfiguration configuration, String scopeId) {
    this.configuration = configuration;
    this.scopeId = scopeId;
    this.factory = new SpringObjectFactory();
    this.converter = ObjectConverterLoader.instance();
    log.debug("SpringTestContext created with scopeId: {}", scopeId);
  }

  /**
   * Create a SpringTestContext using an existing ApplicationContext.
   * This is useful for programmatic setup.
   */
  public SpringTestContext(ApplicationContext applicationContext) {
    this(applicationContext, UUID.randomUUID().toString());
  }

  /**
   * Create a SpringTestContext using an existing ApplicationContext and scope ID.
   */
  public SpringTestContext(ApplicationContext applicationContext, String scopeId) {
    SpringContextHolder.setContext(applicationContext);
    this.configuration = new SpringConfiguration();
    this.scopeId = scopeId;
    this.factory = new SpringObjectFactory();
    this.converter = ObjectConverterLoader.instance();
    log.debug("SpringTestContext created with ApplicationContext: {} and scopeId: {}",
        applicationContext.getId(), scopeId);
  }

  /**
   * Get the unique scope identifier for this test context.
   */
  public String scopeId() {
    return scopeId;
  }

  @Override
  public ObjectFactory factory() {
    return factory;
  }

  @Override
  public ObjectConverter converter() {
    return converter;
  }

  @Override
  public TestConfiguration configuration() {
    return configuration;
  }

  @Override
  public <T> T get(Class<T> type) {
    return get(type, RegistryScope.TEST);
  }

  /**
   * Get an instance of the specified type with the given scope.
   * <p>
   * Resolution order:
   * 1. Try Spring ApplicationContext
   * 2. Try testara-core RootRegistry
   * 3. Create via factory (which uses Spring autowiring)
   */
  public <T> T get(Class<T> type, RegistryScope scope) {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();

    // Try Spring first if available
    if (ctx != null) {
      try {
        T bean = ctx.getBean(type);
        log.trace("Resolved {} from Spring context", type.getName());
        return bean;
      } catch (NoSuchBeanDefinitionException e) {
        log.trace("Bean {} not found in Spring, trying registry", type.getName());
      }
    }

    // Try RootRegistry
    if (RootRegistry.instance().hasProvider(type)) {
      return RootRegistry.instance().get(type, resolveScopeKey(scope));
    }

    // Create via factory (Spring-backed)
    return factory.getInstance(type);
  }

  private String resolveScopeKey(RegistryScope scope) {
    return switch (scope) {
      case GLOBAL -> RegistryScope.GLOBAL.name();
      case THREAD -> Thread.currentThread().getName();
      case TEST -> scopeId();
    };
  }

  @Override
  public boolean has(Class<?> type) {
    // Check Spring context
    if (SpringContextHolder.containsBean(type)) {
      return true;
    }

    // Check RootRegistry
    return RootRegistry.instance().hasInstance(type) ||
        RootRegistry.instance().hasInstance(type, scopeId);
  }

  /**
   * Clear instances for the current test scope.
   * Call this at the end of each test to clean up TEST-scoped instances.
   */
  public void clearTestScope() {
    RootRegistry.instance().clearScope(scopeId);
    log.debug("Cleared test scope: {}", scopeId);
  }
}
