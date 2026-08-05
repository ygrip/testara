package io.github.ygrip.testara.ui.selenium.engine;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.selenium.config.SeleniumDriverProperties;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.selenium.driver.SeleniumSession;
import io.github.ygrip.testara.ui.factory.EngineFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceDimension;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.model.EmulationModel;
import io.github.ygrip.testara.ui.model.RemoteDriverConfig;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unchecked")
public final class SeleniumEngine implements EngineFactory<SeleniumDriverProperties> {
  private static final String ID = "selenium";
  private static final String DEFAULT_REMOTE_URL = "http://localhost:4444/";
  private final ObjectConverter converter;

  public SeleniumEngine() {
    converter = ObjectConverterLoader.instance();
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public SeleniumDriverProperties config() {
    return TestFramework.configuration()
      .get(SeleniumDriverProperties.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SeleniumSession forDriver(String name) throws Exception {
    return forDriver(name, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SeleniumSession forDriver(String name, String deviceType) throws Exception {
    return forDriver(name, deviceType, null);
  }

  private Capabilities toCapabilities(Map<String, Object> mappedObject) {
    MutableCapabilities capabilities = new MutableCapabilities();
    Optional.ofNullable(mappedObject)
      .stream()
      .filter(ObjectUtils::isNotEmpty)
      .map(Map::entrySet)
      .flatMap(Collection::stream)
      .forEach(entry -> {
        capabilities.setCapability(entry.getKey(), parseValue(entry.getValue()));
      });
    return capabilities;
  }

  private MutableCapabilities getCapabilities(String name, DeviceType deviceType) {
    MutableCapabilities capabilities = new MutableCapabilities();
    final var capabilitiesFromProperties = toCapabilities(Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getCapabilities)
      .map(platform -> platform.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .map(cap -> cap.get(name))
      .filter(ObjectUtils::isNotEmpty)
      .orElse(Collections.emptyMap()));
    capabilities.merge(capabilitiesFromProperties);
    return capabilities;
  }

  private EmulationModel getEmulationModel(String name, DeviceType deviceType) {
    return Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getEmulation)
      .map(emulation -> emulation.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .map(model -> model.get(name))
      .orElse(null);
  }

  private DesiredCapabilities getRemoteCapabilities(AbstractDriver<?, ?> targetDriver) {
    final var deviceType = deviceType(targetDriver);
    DesiredCapabilities capabilities = new DesiredCapabilities();
    final var remoteProperties = getRemoteDriverProperties(deviceType);
    Map<String, Object> additional = new HashMap<>();
    additional.put(
      "name",
      Optional.ofNullable(targetDriver)
        .map(AbstractDriver::owner)
        .filter(StringUtils::isNotBlank)
        .orElse("automation")
    );
    final var browserName = browserName(targetDriver);
    final var version = getDesiredVersion(targetDriver);
    if (StringUtils.isNotBlank(browserName)) {
      additional.put("browserName", browserName);
    }
    if (StringUtils.isNotBlank(version)) {
      additional.put("version", version);
    }
    additional.put(
      "enableVNC",
      Optional.ofNullable(remoteProperties)
        .map(RemoteDriverConfig::isEnableVnc)
        .orElse(false)
    );
    additional.put(
      "enableVideo",
      Optional.ofNullable(remoteProperties)
        .map(RemoteDriverConfig::isEnableVideo)
        .orElse(false)
    );
    capabilities.setCapability("selenoid:options", additional);
    return capabilities;
  }

  private String browserName(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(targetDriver)
      .map(AbstractDriver::metadata)
      .map(DriverMetadata::browserName)
      .filter(StringUtils::isNotBlank)
      .orElse(null);
  }

  private DeviceType deviceType(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(targetDriver)
      .map(AbstractDriver::getDeviceType)
      .filter(ObjectUtils::isNotEmpty)
      .orElse(DeviceType.DEFAULT);
  }

  private AvailableProxy proxyType(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(targetDriver)
      .map(AbstractDriver::getProxyType)
      .filter(ObjectUtils::isNotEmpty)
      .orElse(null);
  }

  private RemoteDriverConfig getRemoteDriverProperties(DeviceType deviceType) {
    return Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getRemoteDriver)
      .map(remote -> remote.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .orElse(null);
  }

  private String getDesiredVersion(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getVersion)
      .map(version -> version.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::driverName)
        .filter(StringUtils::isNotBlank)
        .orElse("")))
      .orElse(null);
  }

  private String getDesiredUserAgent(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getUserAgent)
      .map(target -> target.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::getDeviceType)
        .filter(ObjectUtils::isNotEmpty)
        .orElse(DeviceType.DEFAULT)))
      .map(userAgent -> userAgent.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::driverName)
        .filter(StringUtils::isNotBlank)
        .orElse("")))
      .orElse(null);
  }

  private String getBinaryPath(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getBinaryPath)
      .map(target -> target.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::getDeviceType)
        .filter(ObjectUtils::isNotEmpty)
        .orElse(DeviceType.DEFAULT)))
      .map(binaryPath -> binaryPath.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::driverName)
        .filter(StringUtils::isNotBlank)
        .orElse("")))
      .orElse(null);
  }

  private List<String> getArguments(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(SeleniumDriverProperties::getArgs)
      .map(target -> target.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::getDeviceType)
        .filter(ObjectUtils::isNotEmpty)
        .orElse(DeviceType.DEFAULT)))
      .map(arguments -> arguments.get(Optional.ofNullable(targetDriver)
        .map(AbstractDriver::driverName)
        .filter(StringUtils::isNotBlank)
        .orElse(null)))
      .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private <D extends WebDriver, O extends Capabilities> O getOptions(AbstractDriver<D, O> targetDriver) {
    final var deviceType = deviceType(targetDriver);
    final var proxyType = proxyType(targetDriver);
    final var remoteProperties = getRemoteDriverProperties(deviceType);

    MutableCapabilities capabilities = Optional.ofNullable(targetDriver)
      .map(driver -> getCapabilities(driver.driverName(), deviceType))
      .orElseGet(MutableCapabilities::new);
    O defaultOptions = Optional.ofNullable(targetDriver)
      .map(AbstractDriver::defaultOptions)
      .orElse(null);
    if (deviceType.equals(DeviceType.MOBILE)) {
      O mobileOptions = Optional.ofNullable(targetDriver)
        .map(AbstractDriver::mobileOptions)
        .orElse(null);
      if (ObjectUtils.isNotEmpty(defaultOptions) && ObjectUtils.isNotEmpty(mobileOptions)) {
        defaultOptions = (O) defaultOptions.merge(mobileOptions);
      }
    }

    if (ObjectUtils.isNotEmpty(proxyType)) {
      O proxyOptions = Optional.ofNullable(targetDriver)
        .map(AbstractDriver::proxyOptions)
        .orElse(null);
      if (ObjectUtils.isNotEmpty(defaultOptions) && ObjectUtils.isNotEmpty(proxyOptions)) {
        defaultOptions = (O) defaultOptions.merge(proxyOptions);
      }
    }

    if (ObjectUtils.isNotEmpty(defaultOptions)) {
      defaultOptions = (O) defaultOptions.merge(capabilities);
    }

    if (Optional.ofNullable(remoteProperties)
      .map(RemoteDriverConfig::isEnabled)
      .orElse(false)) {
      final var remoteCapabilities = getRemoteCapabilities(targetDriver);
      final var browserName = browserName(targetDriver);
      final var version = getDesiredVersion(targetDriver);
      if (StringUtils.isNotBlank(browserName)) {
        remoteCapabilities.setBrowserName(browserName);
      }
      if (StringUtils.isNotBlank(version)) {
        remoteCapabilities.setVersion(version);
      }

      return (O) remoteCapabilities.merge(Optional.ofNullable(defaultOptions)
        .orElse((O) capabilities));
    }

    return Optional.ofNullable(defaultOptions)
      .orElse((O) capabilities);
  }

  @SuppressWarnings("unchecked")
  private <D extends WebDriver, O extends Capabilities> D remoteDriver(String driverName, String url,
    ClientConfig clientConfig, O options) throws Exception {
    try {
      RemoteWebDriver remoteWebDriver;
      try {
        log.debug(
          "Initializing remote driver with timeouts: connection={}, read={}",
          clientConfig.connectionTimeout(),
          clientConfig.readTimeout()
        );

        remoteWebDriver = (RemoteWebDriver) RemoteWebDriver.builder()
          .address(url)
          .oneOf(options)
          .config(clientConfig)
          .build();
        remoteWebDriver.setFileDetector(new LocalFileDetector());
      } catch (Exception err) {
        log.warn("Error when requesting remote web driver for {}", driverName, err);
        throw new RuntimeException(String.format(
          "Cannot initialize remote driver with error  " + "%s",
          err.getMessage()
        ));
      }
      return (D) remoteWebDriver;
    } catch (Exception e) {
      log.warn("Error when trying to spawn {} with log ", driverName, e);
      throw new RuntimeException(String.format("Cannot initialize remote driver with error  " + "%s", e.getMessage()));
    }
  }

  /**
   * <p>parseValue.</p>
   *
   * @param input a {@link Object} object.
   * @return a {@link Object} object.
   */
  private Object parseValue(Object input) {
    Object value = converter.convert(input);
    Object temp = value instanceof String ? CommonHelper.parseStringToObject(value.toString()) : value;
    return temp == null ? value : temp;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SeleniumSession forDriver(String name, String deviceType, String proxyType) throws Exception {
    final var type = deviceType(deviceType);
    final var proxy = proxyType(proxyType);
    final var driverFullName = constructDriverName(name, type, proxy);

    SeleniumSession session;
    session = (SeleniumSession) DriverSessionManager.inThisTestThread()
      .getDriver(driverFullName);

    if (ObjectUtils.isEmpty(session)) {
      session = forDriver(name, type, proxy);
      DriverSessionManager.inThisTestThread()
        .registerDriver(driverFullName)
        .forDriver(session);
    }
    DriverSessionManager.inThisTestThread()
      .setCurrentActiveDriver(session);

    return session;
  }

  @SuppressWarnings("unchecked")
  private <D extends WebDriver, O extends Capabilities> SeleniumSession forDriver(String name, DeviceType deviceType,
    AvailableProxy proxyType) throws Exception {
    final var drivers = loadDrivers();
    final var driverType = drivers.get(name);

    AbstractDriver<D, O> factory = (AbstractDriver<D, O>) TestFramework.factory()
      .getInstance(driverType);
    final var userAgent = getDesiredUserAgent(factory);
    final var binaryPath = getBinaryPath(factory);
    final var arguments = Optional.ofNullable(getArguments(factory))
      .orElse(Collections.emptyList());
    final var emulation = getEmulationModel(factory.driverName(), deviceType);
    final var version = getDesiredVersion(factory);

    factory.forDevice(deviceType)
      .withBinaryPath(binaryPath)
      .withUserAgent(userAgent)
      .withProxyType(proxyType)
      .withArguments(arguments)
      .withEmulation(emulation)
      .withOwner(Optional.ofNullable(config())
        .map(SeleniumDriverProperties::getOwner)
        .orElse(System.getenv("user.name")))
      .headless(Optional.ofNullable(config())
        .map(SeleniumDriverProperties::isHeadless)
        .orElse(false));

    final var options = getOptions(factory);

    final var remote = getRemoteDriverProperties(deviceType);
    D driver = null;
    if (Optional.ofNullable(remote)
      .map(RemoteDriverConfig::isEnabled)
      .orElse(false)) {
      //Request remote webdriver
      final var url = Optional.of(remote)
        .map(RemoteDriverConfig::getUri)
        .filter(StringUtils::isNotBlank)
        .orElse(DEFAULT_REMOTE_URL);
      log.info(
        "#Requesting remote web driver for {} in {} platform to {} with capabilities :\n{}",
        factory.driverName(),
        deviceType.name()
          .toLowerCase(),
        url,
        options
      );

      final var connectionTimeout = Duration.ofSeconds(Optional.of(remote)
        .map(RemoteDriverConfig::getConnectionTimeoutSeconds)
        .orElse(30));
      final var readTimeout = Duration.ofSeconds(Optional.of(remote)
        .map(RemoteDriverConfig::getReadTimeoutSeconds)
        .orElse(30));
      final var clientConfig = ClientConfig.defaultConfig()
        .connectionTimeout(connectionTimeout)
        .readTimeout(readTimeout);
      driver = remoteDriver(factory.driverName(), url, clientConfig, options);
    } else {
      //Request local webdriver
      WebDriverManager manager = WebDriverManager.getInstance(factory.getDriverType());
      log.debug(
        "Setting up web driver for {} in {} platform with capabilities :\n{}",
        factory.driverName(),
        deviceType.name(),
        options
      );
      if (StringUtils.isNotBlank(version)) {
        manager.browserVersion(version);
      }

      if (config().isClearCache()) {
        try {
          log.debug("Clearing driver cache for {}", factory.driverName());
          manager.clearDriverCache();
        } catch (Exception ignored) {
          log.warn("Fail to clear driver cache for {}", factory.driverName());
        }
      }

      driver = (D) manager.capabilities(options)
        .create();
    }

    if (Optional.ofNullable(emulation)
      .map(EmulationModel::isAdjustDimension)
      .orElse(false)) {
      DeviceDimension dimension = factory.getDimension(emulation);
      driver.manage()
        .window()
        .setPosition(new Point(0, 0));
      driver.manage()
        .window()
        .setSize(new Dimension(dimension.getWidth(), dimension.getHeight()));
    } else if (config().isMaximizeBrowser()) {
      driver.manage()
        .window()
        .maximize();
    }

    return (SeleniumSession) new SeleniumSession().using(driver)
      .on(deviceType);
  }

  @Override
  public Logger log() {
    return log;
  }
}
