package io.github.ygrip.testara.spring.factory;

import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.spring.context.SpringContextHolder;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * Spring-backed ObjectFactory implementation.
 * <p>
 * When Spring is present, this factory takes precedence over DefaultObjectFactory.
 * It resolves beans from the Spring ApplicationContext, enabling:
 * - Spring dependency injection
 * - Spring-managed lifecycle
 * - Integration with @Autowired and @Component annotations
 * <p>
 * Beans not found in Spring context will be created via the fallback factory
 * and optionally registered into Spring for future resolution.
 */
@Log4j2
public final class SpringObjectFactory implements ObjectFactory {

  private static final int SPRING_PRIORITY = 100;

  @Override
  public <T> T getInstance(Class<T> type) {
    ApplicationContext context = SpringContextHolder.getApplicationContext();

    if (context == null) {
      throw new IllegalStateException(
          "Spring ApplicationContext not initialized. Ensure SpringContextHolder is configured.");
    }

    try {
      // Try to get from Spring context first
      return context.getBean(type);
    } catch (NoSuchBeanDefinitionException e) {
      log.debug("Bean not found in Spring context for type: {}. Will attempt to create.", type.getName());
      // Let Spring create it via autowiring if possible
      return createAndRegisterBean(type, context);
    }
  }

  /**
   * Create a bean instance and optionally register it with Spring.
   * Uses Spring's AutowireCapableBeanFactory for proper DI.
   */
  private <T> T createAndRegisterBean(Class<T> type, ApplicationContext context) {
    try {
      // Use Spring's autowiring capability to create the bean
      T instance = context.getAutowireCapableBeanFactory().createBean(type);
      log.debug("Created bean via Spring autowiring: {}", type.getName());
      return instance;
    } catch (Exception e) {
      log.warn("Failed to create bean via Spring autowiring for type: {}. Error: {}",
          type.getName(), e.getMessage());
      throw new RuntimeException("Cannot create instance of " + type.getName(), e);
    }
  }

  @Override
  public boolean supports(Class<?> type) {
    // Support all types when Spring context is available
    return SpringContextHolder.getApplicationContext() != null;
  }

  @Override
  public int priority() {
    // Higher priority than DefaultObjectFactory (0)
    return SPRING_PRIORITY;
  }

  @Override
  public void start() {
    log.trace("SpringObjectFactory started - Spring DI enabled");
  }

  @Override
  public void stop() {
    log.trace("SpringObjectFactory stopped");
  }
}
