package io.github.ygrip.testara.core.config;

import java.util.ServiceLoader;

public final class PropertyResolverLoader {

  private PropertyResolverLoader() {

  }

  public static PropertyResolver load() {
    return ServiceLoader.load(PropertyResolver.class, Thread.currentThread().getContextClassLoader())
        .findFirst()
        .orElse(new DefaultPropertyResolver());
  }
}
