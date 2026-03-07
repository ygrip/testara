package io.github.ygrip.testara.core.factory;

import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * Loader for ObjectFactory implementations using Java SPI.
 * Selects factory based on priority (highest first).
 */
public final class ObjectFactoryLoader {

  private ObjectFactoryLoader() {
  }

  /**
   * Load and select the best ObjectFactory implementation.
   * Selection strategy:
   * 1. Load all implementations via ServiceLoader
   * 2. Sort by priority (descending)
   * 3. Return first factory with highest priority
   * 4. Fall back to DefaultObjectFactory if none found
   *
   * @return the selected ObjectFactory
   */
  public static ObjectFactory load() {
    ServiceLoader<ObjectFactory> loader =
        ServiceLoader.load(ObjectFactory.class, Thread.currentThread().getContextClassLoader());

    return StreamSupport.stream(loader.spliterator(), false)
        .sorted(Comparator.comparingInt(ObjectFactory::priority).reversed())
        .findFirst()
        .orElse(new DefaultObjectFactory());
  }

  /**
   * Select the best ObjectFactory for a specific type.
   * Used when multiple factories are available and need type-specific selection.
   *
   * @param type the class to create
   * @return the best factory that supports the type
   */
  public static ObjectFactory loadFor(Class<?> type) {
    ServiceLoader<ObjectFactory> loader = ServiceLoader.load(ObjectFactory.class);

    return StreamSupport.stream(loader.spliterator(), false)
        .filter(factory -> factory.supports(type))
        .max(Comparator.comparingInt(ObjectFactory::priority))
        .orElse(new DefaultObjectFactory());
  }
}
