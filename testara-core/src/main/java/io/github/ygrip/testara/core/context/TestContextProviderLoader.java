package io.github.ygrip.testara.core.context;

import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * Loader for TestContextProvider implementations using Java SPI.
 * Selects provider based on priority (highest first) and availability.
 */
public final class TestContextProviderLoader {

  private TestContextProviderLoader() {
  }

  /**
   * Load and select the best TestContextProvider implementation.
   * Selection strategy:
   * 1. Load all implementations via ServiceLoader
   * 2. Filter by availability (isAvailable())
   * 3. Sort by priority (descending)
   * 4. Return first provider with highest priority
   * 5. Fall back to DefaultTestContextProvider if none found
   *
   * @return the selected TestContextProvider
   */
  public static TestContextProvider load() {
    ServiceLoader<TestContextProvider> loader =
        ServiceLoader.load(TestContextProvider.class, Thread.currentThread().getContextClassLoader());

    return StreamSupport.stream(loader.spliterator(), false)
        .filter(TestContextProvider::isAvailable)
        .sorted(Comparator.comparingInt(TestContextProvider::priority).reversed())
        .findFirst()
        .orElse(new DefaultTestContextProvider());
  }
}
