package io.github.ygrip.testara.ui.appium.engine;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.appium.config.AppiumDriverProperties;
import io.github.ygrip.testara.ui.appium.driver.AppiumSession;
import io.github.ygrip.testara.ui.appium.model.AppsData;
import io.github.ygrip.testara.ui.appium.remote.AppiumServer;
import io.github.ygrip.testara.ui.appium.remote.AppiumServerManager;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.factory.EngineFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.RemoteDriverConfig;

import io.appium.java_client.AppiumClientConfig;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;
import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unchecked")
public final class AppiumEngine implements EngineFactory<AppiumDriverProperties> {
  private static final String ID = "appium";
  private static final String DEFAULT_REMOTE_URL = "http://localhost:4444/";
  private final ObjectConverter converter;

  public AppiumEngine() {
    converter = ObjectConverterLoader.instance();
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public AppiumDriverProperties config() {
    return TestFramework.configuration()
      .get(AppiumDriverProperties.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public AppiumSession forDriver(String name) throws Exception {
    return forDriver(name, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public AppiumSession forDriver(String name, String deviceType) throws Exception {
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
      .map(AppiumDriverProperties::getCapabilities)
      .map(platform -> platform.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .map(cap -> cap.get(name))
      .filter(ObjectUtils::isNotEmpty)
      .orElse(Collections.emptyMap()));
    capabilities.merge(capabilitiesFromProperties);
    return capabilities;
  }

  private DesiredCapabilities getRemoteCapabilities(AbstractDriver<?, ?> targetDriver) {
    return new DesiredCapabilities();
  }

  private DeviceType deviceType(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(targetDriver)
      .map(AbstractDriver::getDeviceType)
      .filter(ObjectUtils::isNotEmpty)
      .orElse(DeviceType.DEFAULT);
  }

  private AppsData appData(String name, DeviceType deviceType) {
    return Optional.ofNullable(config())
      .map(AppiumDriverProperties::getApps)
      .map(platform -> platform.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .map(apps -> apps.get(name))
      .filter(ObjectUtils::isNotEmpty)
      .orElse(null);
  }

  private AvailableProxy proxyType(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(targetDriver)
      .map(AbstractDriver::getProxyType)
      .filter(ObjectUtils::isNotEmpty)
      .orElse(null);
  }

  private RemoteDriverConfig getRemoteDriverProperties(DeviceType deviceType) {
    return Optional.ofNullable(config())
      .map(AppiumDriverProperties::getRemoteDriver)
      .map(remote -> remote.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .orElse(null);
  }

  private List<String> getArguments(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(AppiumDriverProperties::getArgs)
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

      return (O) remoteCapabilities.merge(Optional.ofNullable(defaultOptions)
        .orElse((O) capabilities));
    }

    return Optional.ofNullable(defaultOptions)
      .orElse((O) capabilities);
  }

  @SuppressWarnings("unchecked")
  private <D extends AppiumDriver, O extends Capabilities> D remoteDriver(String driverName, String url,
    AppiumClientConfig clientConfig, O options) throws Exception {
    try {
      AppiumDriver remoteWebDriver;
      try {
        log.debug(
          "Initializing appium remote driver with timeouts: connection={}, read={}",
          clientConfig.connectionTimeout(),
          clientConfig.readTimeout()
        );

        AppiumServer appiumServer = AppiumServerManager.buildServer(driverName, url);
        if (ObjectUtils.isNotEmpty(appiumServer)) {
          appiumServer.start();
        }

        URI addressUrl = URI.create(url);

        remoteWebDriver = (AppiumDriver) AppiumDriver.builder()
          .oneOf(options)
          .address(addressUrl)
          .config(clientConfig)
          .build();
      } catch (Exception err) {
        log.warn("Error when requesting appium remote driver for {}", driverName, err);
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
  public AppiumSession forDriver(String name, String deviceType, String proxyType) throws Exception {
    final var type = deviceType(deviceType);
    final var proxy = proxyType(proxyType);
    final var driverFullName = constructDriverName(name, type, proxy);

    AppiumSession session;
    session = (AppiumSession) DriverSessionManager.inThisTestThread()
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
  private <D extends AppiumDriver, O extends Capabilities> AppiumSession forDriver(String name, DeviceType deviceType,
    AvailableProxy proxyType) throws Exception {
    final var drivers = loadDrivers();
    final var driverType = drivers.get(name);

    AbstractDriver<D, O> factory = (AbstractDriver<D, O>) TestFramework.factory()
      .getInstance(driverType);
    final var arguments = Optional.ofNullable(getArguments(factory))
      .orElse(Collections.emptyList());

    factory.forDevice(deviceType)
      .withProxyType(proxyType)
      .withArguments(arguments)
      .withOwner(Optional.ofNullable(config())
        .map(AppiumDriverProperties::getOwner)
        .orElse(System.getenv("user.name")))
      .headless(Optional.ofNullable(config())
        .map(AppiumDriverProperties::isHeadless)
        .orElse(false));

    final var appData = appData(name, deviceType);
    final var options = constructAppData(getOptions(factory), appData);

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
      final var clientConfig = AppiumClientConfig.defaultConfig()
        .connectionTimeout(connectionTimeout)
        .readTimeout(readTimeout);
      driver = remoteDriver(factory.driverName(), url, clientConfig, options);
    } else {

      log.debug(
        "Setting up web driver for {} in {} platform with capabilities :\n{}",
        factory.driverName(),
        deviceType.name(),
        options
      );

      driver = factory.create(options);
    }

    //install desired apps if not installed and launch it
    ensureInstalled(driver, appData);
    openApp(driver, appData);
    return (AppiumSession) new AppiumSession().using(driver)
      .on(deviceType);
  }

  private <D extends AppiumDriver> void openApp(D driver, AppsData appData) {
    if (driver instanceof InteractsWithApps interactsWithApps) {
      interactsWithApps.activateApp(appData.getAppPackage());
    }
  }

  private <D extends AppiumDriver> void ensureInstalled(D driver, AppsData appData) {
    if (driver instanceof InteractsWithApps interactsWithApps) {
      if (!interactsWithApps.isAppInstalled(appData.getAppPackage())) {
        interactsWithApps.installApp(appData.getFileLocation());
      }
    }
  }

  private <O> O constructAppData(O capabilities, AppsData appsData) {
    if (ObjectUtils.isNotEmpty(appsData)) {
      if (capabilities instanceof UiAutomator2Options androidCapabilities) {
        if (appsData.isResetInstall()) {
          androidCapabilities.setFullReset(true);
        } else {
          androidCapabilities.setNoReset(true)
            .setFullReset(false);
        }
        androidCapabilities.setAppPackage(appsData.getAppPackage());
      } else if (capabilities instanceof XCUITestOptions iosCapabilities) {
        if (appsData.isResetInstall()) {
          iosCapabilities.setFullReset(true);
        } else {
          iosCapabilities.setNoReset(true)
            .setFullReset(false);
        }
        iosCapabilities.setBundleId(appsData.getAppPackage());
      }
    }
    return capabilities;
  }

  @Override
  public Logger log() {
    return log;
  }
}
