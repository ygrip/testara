package io.github.ygrip.testara.spring.context;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestContextProvider;
import io.github.ygrip.testara.spring.config.SpringConfiguration;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationContext;

/**
 * Spring-backed TestContextProvider implementation.
 * <p>
 * When testara-spring is on the classpath, this provider takes precedence
 * over the default provider, creating SpringTestContext instances.
 * <p>
 * This provider is available when:
 * - Spring ApplicationContext is initialized in SpringContextHolder
 * - OR when this provider can initialize Spring context automatically
 */
@Log4j2
public final class SpringTestContextProvider implements TestContextProvider {

  private static final int SPRING_PRIORITY = 100;

  @Override
  public TestContext create(TestConfiguration configuration, String scopeId) {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();

    if (ctx != null) {
      log.trace("Creating SpringTestContext with existing ApplicationContext: {}", ctx.getId());
      return new SpringTestContext(ctx, scopeId);
    }

    // Use provided configuration or create Spring-aware configuration
    TestConfiguration springConfig = configuration;
    if (!(configuration instanceof SpringConfiguration)) {
      springConfig = new SpringConfiguration();
    }

    log.trace("Creating SpringTestContext with SpringConfiguration");
    return new SpringTestContext(springConfig, scopeId);
  }

  @Override
  public int priority() {
    // Higher priority than DefaultTestContextProvider (0)
    return SPRING_PRIORITY;
  }

  @Override
  public boolean isAvailable() {
    // Available when Spring classes are on classpath
    // The actual ApplicationContext initialization happens lazily
    try {
      Class.forName("org.springframework.context.ApplicationContext");
      log.trace("SpringTestContextProvider is available (Spring on classpath)");
      return true;
    } catch (ClassNotFoundException e) {
      log.trace("SpringTestContextProvider not available (Spring not on classpath)");
      return false;
    }
  }
}
