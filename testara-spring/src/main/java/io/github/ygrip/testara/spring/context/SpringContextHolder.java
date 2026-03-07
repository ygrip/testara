package io.github.ygrip.testara.spring.context;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds a static reference to the Spring ApplicationContext.
 * <p>
 * This enables non-Spring components (like testara-core) to access Spring beans.
 * The context is set automatically when Spring detects this component via component scanning.
 * <p>
 * Thread-safe and supports context refresh scenarios.
 */
@Log4j2
@Component("io.github.ygrip.testara.spring.SpringContextHolder")
public final class SpringContextHolder implements ApplicationContextAware {

  private static final AtomicReference<ApplicationContext> CONTEXT = new AtomicReference<>();
  private static final AtomicReference<ConfigurableListableBeanFactory> BEAN_FACTORY = new AtomicReference<>();

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    log.trace("SpringContextHolder initialized with ApplicationContext: {}", applicationContext.getId());
    CONTEXT.set(applicationContext);
    
    if (applicationContext instanceof ConfigurableApplicationContext configurable) {
      BEAN_FACTORY.set(configurable.getBeanFactory());
    }
  }

  /**
   * Get the current ApplicationContext.
   * 
   * @return the ApplicationContext, or null if not initialized
   */
  public static ApplicationContext getApplicationContext() {
    return CONTEXT.get();
  }

  /**
   * Get the current BeanFactory.
   * 
   * @return the ConfigurableListableBeanFactory, or null if not initialized
   */
  public static ConfigurableListableBeanFactory getBeanFactory() {
    return BEAN_FACTORY.get();
  }

  /**
   * Get a bean from the Spring context.
   * 
   * @param type the bean type
   * @param <T> the type parameter
   * @return the bean instance
   * @throws IllegalStateException if context is not initialized
   */
  public static <T> T getBean(Class<T> type) {
    ApplicationContext ctx = CONTEXT.get();
    if (ctx == null) {
      throw new IllegalStateException("ApplicationContext not initialized");
    }
    return ctx.getBean(type);
  }

  /**
   * Get a bean from the Spring context by name.
   * 
   * @param name the bean name
   * @param type the bean type
   * @param <T> the type parameter
   * @return the bean instance
   * @throws IllegalStateException if context is not initialized
   */
  public static <T> T getBean(String name, Class<T> type) {
    ApplicationContext ctx = CONTEXT.get();
    if (ctx == null) {
      throw new IllegalStateException("ApplicationContext not initialized");
    }
    return ctx.getBean(name, type);
  }

  /**
   * Check if a bean of the given type exists.
   * 
   * @param type the bean type
   * @return true if a bean of this type exists
   */
  public static boolean containsBean(Class<?> type) {
    ApplicationContext ctx = CONTEXT.get();
    if (ctx == null) {
      return false;
    }
    try {
      ctx.getBean(type);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Check if a bean with the given name exists.
   * 
   * @param name the bean name
   * @return true if a bean with this name exists
   */
  public static boolean containsBean(String name) {
    ApplicationContext ctx = CONTEXT.get();
    if (ctx == null) {
      return false;
    }
    return ctx.containsBean(name);
  }

  /**
   * Check if the context is initialized.
   * 
   * @return true if context is available
   */
  public static boolean isInitialized() {
    return CONTEXT.get() != null;
  }

  /**
   * Clear the context reference.
   * Used during shutdown or testing.
   */
  public static void clear() {
    log.debug("Clearing SpringContextHolder");
    CONTEXT.set(null);
    BEAN_FACTORY.set(null);
  }

  /**
   * Manually set the ApplicationContext.
   * Useful for programmatic configuration without component scanning.
   * 
   * @param context the ApplicationContext to set
   */
  public static void setContext(ApplicationContext context) {
    CONTEXT.set(context);
    if (context instanceof ConfigurableApplicationContext configurable) {
      BEAN_FACTORY.set(configurable.getBeanFactory());
    }
    log.trace("SpringContextHolder manually configured with ApplicationContext: {}",
        context != null ? context.getId() : "null");
  }
}
