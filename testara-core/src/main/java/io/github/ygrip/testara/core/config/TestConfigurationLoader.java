package io.github.ygrip.testara.core.config;

import java.util.ServiceLoader;

public final class TestConfigurationLoader {

  private TestConfigurationLoader() {

  }

  public static TestConfiguration load() {
    return ServiceLoader.load(TestConfiguration.class, Thread.currentThread().getContextClassLoader())
        .findFirst()
        .orElse(new DefaultConfiguration());
  }
}
