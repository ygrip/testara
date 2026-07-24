package io.github.ygrip.testara.ui.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.Stopwatch;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.UnrecognizedApplicationException;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;

public interface EngineFactory<T extends AbstractDriverProperties> {
  Map<Class<? extends EngineFactory<?>>, Map<String, Class<? extends AbstractDriver<?, ?>>>> DRIVERS =
    new ConcurrentHashMap<>();

  String id();

  T config();

  default String constructDriverName(String name, DeviceType deviceType, AvailableProxy proxyType) {
    if (StringUtils.isBlank(name)) {
      throw new UnrecognizedApplicationException("Driver name cannot be empty");
    }
    final String separator = "-";
    StringBuilder builder = new StringBuilder();
    builder.append(name.trim());
    builder.append(separator);
    builder.append(deviceType.name()
      .toLowerCase());
    if (!CommonHelper.isBlank(proxyType)) {
      builder.append(separator);
      builder.append(proxyType.name()
        .toLowerCase());
    }
    return builder.toString()
      .replaceAll(":", separator)
      .toLowerCase();
  }

  <D extends DriverSession<?>> D forDriver(String name) throws Exception;

  <D extends DriverSession<?>> D forDriver(String name, String deviceType) throws Exception;

  <D extends DriverSession<?>> D forDriver(String name, String deviceType, String proxyType) throws Exception;

  default DeviceType deviceType(String deviceType) {
    return Optional.ofNullable(deviceType)
      .filter(StringUtils::isNotBlank)
      .map(device -> CommonHelper.searchEnum(DeviceType.class, device))
      .orElse(DeviceType.DEFAULT);
  }

  default AvailableProxy proxyType(String proxyType) {
    return Optional.ofNullable(proxyType)
      .filter(StringUtils::isNotBlank)
      .map(proxy -> CommonHelper.searchEnum(AvailableProxy.class, proxy))
      .orElse(null);
  }

  Logger log();



  @SuppressWarnings("unchecked")
  default Map<String, Class<? extends AbstractDriver<?, ?>>> loadDrivers() {
    if (!DRIVERS.containsKey(this.getClass())) {
      Stopwatch stopwatch = Stopwatch.start();

      T properties = config();

      ClassScanner scanner = TestFramework.context()
        .get(ClassScanner.class);
      List<Class<?>> drivers = new ArrayList<>();
      try {
        drivers = scanner.scanOnPackages(AbstractDriver.class, DriverMetadata.class, properties.getScanLocations())
          .get(10, TimeUnit.SECONDS);
      } catch (Exception err) {
        log().warn("Failed to populate driver for {}", getClass().getSimpleName(), err);
      }
      Map<String, Class<? extends AbstractDriver<?, ?>>> mapped =
        DRIVERS.getOrDefault(this.getClass(), new ConcurrentHashMap<>());
      drivers.forEach(driver -> {
        DriverMetadata metadata = driver.getAnnotation(DriverMetadata.class);
        Class<? extends EngineFactory<?>> engine = metadata.engine();
        String driverName = metadata.name();
        if (this.getClass()
          .isAssignableFrom(engine)) {
          mapped.put(driverName, (Class<? extends AbstractDriver<?, ?>>) driver);
        }
      });
      DRIVERS.put((Class<? extends EngineFactory<?>>) this.getClass(), mapped);
      log().info(
        "#Populating drivers for engine {} took {}. Found {} drivers : {}",
        id(),
        DurationParser.formatDuration(stopwatch.stop()
          .elapsed(TimeUnit.NANOSECONDS)),
        DRIVERS.get(this.getClass()).size(),
        DRIVERS.get(this.getClass()).keySet()
      );
    }

    return DRIVERS.get(this.getClass());
  }
}
