package io.github.ygrip.testara.spring.config;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.spring.bridge.TestaraSpringBridge;
import io.github.ygrip.testara.spring.context.SpringContextHolder;
import io.github.ygrip.testara.spring.context.SpringTestContext;
import io.github.ygrip.testara.spring.factory.SpringObjectFactory;
import io.github.ygrip.testara.spring.processor.ThreadBeanFactoryPostProcessor;
import io.github.ygrip.testara.spring.support.GlobalShutdownManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Main Spring configuration for testara-spring integration.
 * <p>
 * This configuration automatically sets up:
 * - SpringContextHolder for static context access
 * - TestaraSpringBridge for bidirectional bean resolution
 * - TestFramework with Spring-backed TestContext
 * - Custom AutomationScope for thread-scoped beans
 * - ObjectFactory, ObjectConverter, and TestConfiguration beans
 * <p>
 * Import this configuration in your Spring application:
 * {@code @Import(AutomationConfiguration.class)}
 * <p>
 * Or enable component scanning for the package:
 * {@code @ComponentScan("io.github.ygrip.testara.spring")}
 */
@Log4j2
@Configuration
public class AutomationConfiguration {

  /**
   * Register the custom automation scope.
   */
  @Bean
  public static BeanFactoryPostProcessor automationScopeRegistrar() {
    return new ThreadBeanFactoryPostProcessor();
  }

  /**
   * The main SpringContextHolder - enables static access to ApplicationContext.
   */
  @Bean
  public SpringContextHolder springContextHolder() {
    return new SpringContextHolder();
  }

  /**
   * Global shutdown manager for clean context shutdown.
   */
  @Bean
  public GlobalShutdownManager globalShutdownManager() {
    return new GlobalShutdownManager();
  }

  /**
   * Spring-backed ObjectFactory.
   */
  @Bean
  public ObjectFactory objectFactory() {
    return new SpringObjectFactory();
  }

  /**
   * Object converter from testara-core (SPI-loaded).
   */
  @Bean
  public ObjectConverter objectConverter() {
    return ObjectConverterLoader.instance();
  }

  /**
   * Spring-backed TestConfiguration.
   */
  @Bean
  public TestConfiguration testConfiguration() {
    return new SpringConfiguration();
  }

  /**
   * Spring-backed TestContext.
   * This is prototype-scoped so each test can have its own context.
   */
  @Bean
  @Scope("prototype")
  public TestContext testContext(TestConfiguration configuration) {
    return new SpringTestContext(configuration);
  }

  /**
   * Initialize the TestaraSpringBridge and TestFramework.
   * This is called after the ApplicationContext is fully initialized.
   */
  @Bean
  public TestaraSpringBridgeInitializer testaraSpringBridgeInitializer(ApplicationContext context) {
    return new TestaraSpringBridgeInitializer(context);
  }

  /**
   * Helper class to initialize the bridge after context is ready.
   */
  @Log4j2
  public static class TestaraSpringBridgeInitializer {
    
    public TestaraSpringBridgeInitializer(ApplicationContext context) {
      log.trace("Initializing TestaraSpringBridge with ApplicationContext: {}", context.getId());
      
      // Initialize the bridge
      TestaraSpringBridge.instance().initialize(context);
      
      // Ensure TestFramework is initialized with Spring context
      if (!isTestFrameworkInitialized()) {
        TestContext springContext = new SpringTestContext(context);
        TestFramework.initialize(springContext);
        log.trace("TestFramework initialized with SpringTestContext");
      }
    }
    
    private boolean isTestFrameworkInitialized() {
      try {
        TestFramework.context();
        return true;
      } catch (IllegalStateException e) {
        return false;
      }
    }
  }
}
