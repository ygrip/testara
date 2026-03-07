package io.github.ygrip.testara.core.config;

import java.util.ServiceLoader;

public final class PropertyNameStrategyLoader {

  private PropertyNameStrategyLoader() {

  }

  public static PropertyNameStrategy load() {
    return ServiceLoader.load(PropertyNameStrategy.class, Thread.currentThread().getContextClassLoader())
        .findFirst()
        .orElse(new RelaxedPropertyNameStrategy());
  }
}
