package io.github.ygrip.testara.spring.support;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
@Component("io.github.ygrip.testara.core.support.GlobalShutdownManager")
public class GlobalShutdownManager implements ApplicationContextAware, DisposableBean {
  private final AtomicBoolean registered = new AtomicBoolean(false);
  private ConfigurableApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    if (applicationContext instanceof ConfigurableApplicationContext) {
      this.context = (ConfigurableApplicationContext) applicationContext;
      registerGlobalShutdownHook();
    } else {
      throw new IllegalStateException("ApplicationContext is not configurable: " + applicationContext.getClass());
    }
  }

  private void registerGlobalShutdownHook() {
    if (registered.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("global-spring-shutdown").unstarted(() -> {
        log.trace("Global Spring shutdown hook triggered");
        safeCloseContext();
      }));
    }
  }

  private void safeCloseContext() {
    try {
      if (context != null && context.isActive()) {
        context.close();
      }
    } catch (Exception e) {
      log.error("Error while closing context: {}", e.getMessage());
    }
  }

  @Override
  public void destroy() throws Exception {
    log.trace("Calling Global Spring Shutdown Manager");
  }
}
