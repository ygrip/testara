package io.github.ygrip.testara.spring;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import io.github.ygrip.testara.spring.bridge.TestaraSpringBridge;
import io.github.ygrip.testara.spring.config.AutomationConfiguration;
import io.github.ygrip.testara.spring.config.SpringConfiguration;
import io.github.ygrip.testara.spring.context.SpringContextHolder;
import io.github.ygrip.testara.spring.context.SpringTestContext;
import io.github.ygrip.testara.spring.factory.SpringObjectFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for testara-spring module.
 * Demonstrates bidirectional bean access between Spring and testara-core.
 */
@DisplayName("Spring Integration Tests")
class SpringIntegrationTest {

  private AnnotationConfigApplicationContext context;

  @BeforeEach
  void setUp() {
    // Create a Spring context with our configuration
    context = new AnnotationConfigApplicationContext();
    context.register(TestSpringConfig.class);
    context.refresh();
  }

  @AfterEach
  void tearDown() {
    if (context != null && context.isActive()) {
      context.close();
    }
    SpringContextHolder.clear();
    TestaraSpringBridge.instance().reset();
    TestFramework.clear();
  }

  @Test
  @DisplayName("SpringContextHolder should be initialized after context refresh")
  void springContextHolderInitialized() {
    assertTrue(SpringContextHolder.isInitialized());
    assertNotNull(SpringContextHolder.getApplicationContext());
  }

  @Test
  @DisplayName("TestaraSpringBridge should be initialized")
  void testaraSpringBridgeInitialized() {
    assertTrue(TestaraSpringBridge.instance().isInitialized());
  }

  @Test
  @DisplayName("TestFramework should be initialized with SpringTestContext")
  void testFrameworkInitialized() {
    assertNotNull(TestFramework.context());
    assertInstanceOf(SpringTestContext.class, TestFramework.context());
  }

  @Test
  @DisplayName("Spring beans should be accessible via TestFramework")
  void springBeansAccessibleViaTestara() {
    // SampleService is a Spring @Component
    SampleService service = TestFramework.context().get(SampleService.class);
    assertNotNull(service);
    assertEquals("Hello from Spring!", service.getMessage());
  }

  @Test
  @DisplayName("ObjectFactory should be Spring-backed")
  void objectFactoryIsSpringBacked() {
    ObjectFactory factory = context.getBean(ObjectFactory.class);
    assertNotNull(factory);
    assertInstanceOf(SpringObjectFactory.class, factory);
  }

  @Test
  @DisplayName("TestConfiguration should be Spring-backed")
  void testConfigurationIsSpringBacked() {
    TestConfiguration config = context.getBean(TestConfiguration.class);
    assertNotNull(config);
    assertInstanceOf(SpringConfiguration.class, config);
  }

  @Test
  @DisplayName("Properties from Spring Environment should be accessible")
  void springPropertiesAccessible() {
    TestConfiguration config = TestFramework.context().configuration();
    
    // Properties defined in @PropertySource or application.properties
    // For this test, we rely on System properties or Environment
    String javaHome = config.get("java.home", "not-found");
    assertNotEquals("not-found", javaHome);
  }

  @Test
  @DisplayName("Non-Spring classes can be instantiated via Spring autowiring")
  void nonSpringClassesCanBeAutowired() {
    // NonSpringClass is not a @Component but has dependencies
    NonSpringClass instance = TestFramework.context().get(NonSpringClass.class);
    assertNotNull(instance);
    assertNotNull(instance.getService());
  }

  // --------------------------------------------------
  // Test Configuration and Sample Components
  // --------------------------------------------------

  @Configuration
  @Import(AutomationConfiguration.class)
  static class TestSpringConfig {
    
    @Bean
    SampleService sampleService() {
      return new SampleService();
    }
  }

  @Component
  static class SampleService {
    String getMessage() {
      return "Hello from Spring!";
    }
  }

  /**
   * A non-Spring class that has dependencies.
   * This tests that Spring can create it via autowiring.
   */
  static class NonSpringClass {
    private final SampleService service;

    @Autowired
    public NonSpringClass(SampleService service) {
      this.service = service;
    }

    SampleService getService() {
      return service;
    }
  }
}
