package io.github.ygrip.testara.ui.registry;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import io.github.ygrip.testara.ui.error.UnrecognizedProxyException;
import io.github.ygrip.testara.ui.factory.ProxyFactory;

public final class ProxyAutomationRegistry {
  private static final Map<Class<?>, ProxyFactory<?>> FACTORIES = new ConcurrentHashMap<>();

  private ProxyAutomationRegistry() {

  }

  private static Map<Class<?>, ProxyFactory<?>> factories() {
    if (FACTORIES.isEmpty()) {
      ServiceLoader.load(
          ProxyFactory.class,
          Thread.currentThread()
            .getContextClassLoader()
        )
        .iterator()
        .forEachRemaining(factory -> {
          FACTORIES.put(factory.getClass(), factory);
        });
    }
    return FACTORIES;
  }


  @SuppressWarnings("unchecked")
  public static <T extends ProxyFactory<?>> T forProxy(Class<T> proxyType) {
    Collection<ProxyFactory<?>> engines = factories().values();
    return (T) engines.stream()
      .filter(engine -> proxyType.isAssignableFrom(engine.getClass()))
      .findAny()
      .orElseThrow(() -> new UnrecognizedProxyException(
        "Proxy with type " + proxyType.getName() + " could not be found / not active on classpath."));
  }
}
