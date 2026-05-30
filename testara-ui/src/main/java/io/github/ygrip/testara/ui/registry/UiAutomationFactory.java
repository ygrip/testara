package io.github.ygrip.testara.ui.registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.error.InvalidConfigurationPropertiesException;
import io.github.ygrip.testara.ui.config.EngineProperties;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.error.DuplicateEngineIdException;
import io.github.ygrip.testara.ui.error.EngineNotFoundException;
import io.github.ygrip.testara.ui.factory.EngineFactory;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverCatalogEntry;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.model.EngineCatalogEntry;

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

  public static List<EngineCatalogEntry> listAvailableEngines() {
    return factories().entrySet().stream()
      .map(e -> {
        String engineId = e.getKey();
        EngineFactory<?> factory = e.getValue();
        List<DriverCatalogEntry> drivers = listDriversForEngine(factory);
        return new EngineCatalogEntry(
          engineId,
          factory.getClass().getSimpleName(),
          drivers
        );
      })
      .sorted(Comparator.comparing(EngineCatalogEntry::id))
      .collect(Collectors.toList());
  }

  public static List<DriverCatalogEntry> listAvailableDrivers() {
    return listAvailableEngines().stream()
      .flatMap(e -> e.drivers().stream())
      .sorted(Comparator.comparing(DriverCatalogEntry::name))
      .collect(Collectors.toList());
  }

  private static List<DriverCatalogEntry> listDriversForEngine(EngineFactory<?> factory) {
    Map<String, Class<? extends AbstractDriver<?, ?>>> drivers = factory.loadDrivers();
    return drivers.entrySet().stream()
      .map(e -> {
        DriverMetadata meta = e.getValue().getAnnotation(DriverMetadata.class);
        List<String> platforms = meta != null
          ? Arrays.stream(meta.platforms()).map(DeviceType::name).collect(Collectors.toList())
          : List.of();
        String browser = meta != null ? meta.browserName() : "";
        String engineId = factory.id();
        return new DriverCatalogEntry(e.getKey(), engineId, platforms, browser);
      })
      .sorted(Comparator.comparing(DriverCatalogEntry::name))
      .collect(Collectors.toList());
  }
}
