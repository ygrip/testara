package io.github.ygrip.testara.ui.registry;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.error.InvalidConfigurationPropertiesException;
import io.github.ygrip.testara.ui.config.EngineProperties;
import io.github.ygrip.testara.ui.error.DuplicateEngineIdException;
import io.github.ygrip.testara.ui.error.EngineNotFoundException;
import io.github.ygrip.testara.ui.factory.EngineFactory;

public final class UiAutomationFactory {
  private static final Map<String, EngineFactory<?>> FACTORIES = new ConcurrentHashMap<>();

  private UiAutomationFactory() {

  }

  private static Map<String, EngineFactory<?>> factories() {
    if (FACTORIES.isEmpty()) {
      EngineProperties config = TestFramework.configuration()
        .get(EngineProperties.class);
      final var activeEngines = config.getActiveEngines()
        .stream()
        .map(String::toLowerCase)
        .map(String::trim)
        .collect(Collectors.toSet());
      ServiceLoader.load(
          EngineFactory.class,
          Thread.currentThread()
            .getContextClassLoader()
        )
        .iterator()
        .forEachRemaining(factory -> {
          String engineName = factory.id()
            .trim()
            .toLowerCase();
          boolean valid = true;
          if (ObjectUtils.isNotEmpty(activeEngines)) {
            valid = activeEngines.contains(engineName);
          }
          if (FACTORIES.containsKey(engineName)) {
            throw new DuplicateEngineIdException(
              "Found duplicate engine with id " + engineName + " on classpath. Engine id should be unique");
          }
          if (valid) {
            FACTORIES.put(engineName, factory);
          }
        });
    }
    return FACTORIES;
  }

  public static EngineFactory<?> forEngine(String engineName) {
    if (StringUtils.isBlank(engineName)) {
      throw new InvalidConfigurationPropertiesException("Malformed properties set for default engine");
    }
    EngineFactory<?> candidate = factories().get(engineName);
    if (ObjectUtils.isEmpty(candidate)) {
      throw new EngineNotFoundException(
        "Engine with id " + engineName + " could not be found / not active on classpath.");
    }
    return candidate;
  }

  @SuppressWarnings("unchecked")
  public static <T> T forEngine(Class<T> engineType) {
    Collection<EngineFactory<?>> engines = factories().values();
    return (T) engines.stream()
      .filter(engine -> engineType.isAssignableFrom(engine.getClass()))
      .findAny()
      .orElseThrow(() -> new DuplicateEngineIdException(
        "Engine with type " + engineType.getName() + " could not be found / not active on classpath."));
  }
}
