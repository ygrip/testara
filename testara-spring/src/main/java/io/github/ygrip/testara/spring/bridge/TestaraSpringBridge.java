package io.github.ygrip.testara.spring.bridge;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.spring.context.SpringContextHolder;
import io.github.ygrip.testara.spring.context.SpringTestContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bidirectional bridge between testara-core and Spring.
 * <p>
 * This bridge ensures:
 * - Beans registered in testara-core are accessible in Spring
 * - Beans registered in Spring are accessible in testara-core
 * - Scope mappings are correctly handled (GLOBAL, THREAD, TEST)
 * <p>
 * Usage:
 * 1. Call {@link #initialize(ApplicationContext)} during application startup
 * 2. Beans registered via RootRegistry become available in Spring
 * 3. Spring beans become available via TestFramework.context().get()
 */
@Log4j2
public final class TestaraSpringBridge {

  private static final TestaraSpringBridge INSTANCE = new TestaraSpringBridge();
  
  private final Set<Class<?>> registeredInSpring = ConcurrentHashMap.newKeySet();
  private volatile boolean initialized = false;

  private TestaraSpringBridge() {}

  public static TestaraSpringBridge instance() {
    return INSTANCE;
  }

  /**
   * Initialize the bridge with a Spring ApplicationContext.
   * This sets up the bidirectional bean access.
   */
  public synchronized void initialize(ApplicationContext context) {
    if (initialized) {
      log.debug("TestaraSpringBridge already initialized");
      return;
    }

    // Set up SpringContextHolder
    SpringContextHolder.setContext(context);

    // Initialize TestFramework with Spring-backed context
    SpringTestContext testContext = new SpringTestContext(context);
    TestFramework.initialize(testContext);

    initialized = true;
    log.trace("TestaraSpringBridge initialized with ApplicationContext: {}", context.getId());
  }

  /**
   * Register a testara-core instance as a Spring bean.
   * 
   * @param type the bean type
   * @param instance the instance to register
   * @param scope the testara scope (maps to Spring scope)
   */
  public <T> void registerInSpring(Class<T> type, T instance, RegistryScope scope) {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();
    if (ctx == null) {
      log.debug("Spring context not available, skipping Spring registration for: {}", type.getName());
      return;
    }

    if (!(ctx instanceof ConfigurableApplicationContext configCtx)) {
      log.warn("ApplicationContext is not configurable, cannot register bean: {}", type.getName());
      return;
    }

    if (registeredInSpring.contains(type)) {
      log.trace("Type {} already registered in Spring", type.getName());
      return;
    }

    try {
      BeanDefinitionRegistry registry = (BeanDefinitionRegistry) configCtx.getBeanFactory();
      String beanName = generateBeanName(type);
      
      // Check if bean already exists
      if (registry.containsBeanDefinition(beanName)) {
        log.trace("Bean {} already defined in Spring", beanName);
        return;
      }

      BeanDefinition definition = BeanDefinitionBuilder
          .genericBeanDefinition(type, () -> instance)
          .setScope(mapToSpringScope(scope))
          .getBeanDefinition();

      registry.registerBeanDefinition(beanName, definition);
      registeredInSpring.add(type);
      
      log.debug("Registered testara bean in Spring: {} with scope {}", beanName, scope);
    } catch (Exception e) {
      log.warn("Failed to register bean {} in Spring: {}", type.getName(), e.getMessage());
    }
  }

  /**
   * Get a bean from either Spring or testara-core.
   * Spring is checked first, then testara's RootRegistry.
   */
  public <T> T get(Class<T> type) {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();
    
    // Try Spring first
    if (ctx != null) {
      try {
        return ctx.getBean(type);
      } catch (NoSuchBeanDefinitionException e) {
        log.trace("Bean {} not found in Spring, trying testara registry", type.getName());
      }
    }

    // Try testara-core
    if (RootRegistry.instance().hasProvider(type)) {
      return RootRegistry.instance().get(type);
    }

    throw new NoSuchBeanDefinitionException(type,
        "Bean not found in Spring or testara-core registry");
  }

  /**
   * Check if a bean exists in either Spring or testara-core.
   */
  public boolean has(Class<?> type) {
    if (SpringContextHolder.containsBean(type)) {
      return true;
    }
    return RootRegistry.instance().hasProvider(type);
  }

  /**
   * Register all Spring beans of a given type into testara-core.
   * Useful for bootstrapping Spring beans into the testara ecosystem.
   */
  public <T> void registerSpringBeansInTestara(Class<T> type, RegistryScope scope) {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();
    if (ctx == null) {
      return;
    }

    Map<String, T> beans = ctx.getBeansOfType(type);
    for (Map.Entry<String, T> entry : beans.entrySet()) {
      T bean = entry.getValue();
      RootRegistry.instance().register(bean, scope);
      log.debug("Registered Spring bean {} in testara registry with scope {}", 
          entry.getKey(), scope);
    }
  }

  /**
   * Map testara RegistryScope to Spring scope name.
   */
  private String mapToSpringScope(RegistryScope scope) {
    return switch (scope) {
      case GLOBAL -> "singleton";
      case THREAD -> "testara-automation"; // Custom automation scope
      case TEST -> "prototype"; // Each test gets a new instance
    };
  }

  /**
   * Generate a Spring bean name from a type.
   */
  private String generateBeanName(Class<?> type) {
    String simpleName = type.getSimpleName();
    return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
  }

  /**
   * Check if the bridge is initialized.
   */
  public boolean isInitialized() {
    return initialized;
  }

  /**
   * Reset the bridge state.
   * Used for testing or context refresh.
   */
  public synchronized void reset() {
    registeredInSpring.clear();
    initialized = false;
    log.debug("TestaraSpringBridge reset");
  }
}
