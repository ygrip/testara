package io.github.ygrip.testara.ui.playwright.engine;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.factory.EngineFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceDimension;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.EmulationModel;
import io.github.ygrip.testara.ui.model.RemoteDriverConfig;
import io.github.ygrip.testara.ui.playwright.config.PlaywrightDriverProperties;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import io.github.ygrip.testara.ui.playwright.driver.StealthProvider;
import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unchecked")
public final class PlaywrightEngine implements EngineFactory<PlaywrightDriverProperties> {
  private static final String ID = "playwright";
  private static final String DEFAULT_REMOTE_URL = "ws://localhost:3000/";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public PlaywrightDriverProperties config() {
    return TestFramework.configuration()
      .get(PlaywrightDriverProperties.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlaywrightSession forDriver(String name) throws Exception {
    return forDriver(name, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlaywrightSession forDriver(String name, String deviceType) throws Exception {
    return forDriver(name, deviceType, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlaywrightSession forDriver(String name, String deviceType, String proxyType) throws Exception {
    final var type = deviceType(deviceType);
    final var proxy = proxyType(proxyType);
    final var driverFullName = constructDriverName(name, type, proxy);

    PlaywrightSession session;
    session = (PlaywrightSession) DriverSessionManager.inThisTestThread()
      .getDriver(driverFullName);

    if (ObjectUtils.isEmpty(session)) {
      session = forDriver(name, type, proxy);
      DriverSessionManager.inThisTestThread()
        .registerDriver(driverFullName)
        .forDriver(session);
    }
    // Same thread as the test: always mark this session current so PageContext / Actor see it,
    // including cache hits (Selenium/Appium engines use the same rule).
    DriverSessionManager.inThisTestThread()
      .setCurrentActiveDriver(session);

    return session;
  }

  @SuppressWarnings("unchecked")
  private PlaywrightSession forDriver(String name, DeviceType deviceType, AvailableProxy proxyType) throws Exception {
    final var drivers = loadDrivers();
    final var driverType = drivers.get(name);

    AbstractDriver<Browser, BrowserType.LaunchOptions> factory =
      (AbstractDriver<Browser, BrowserType.LaunchOptions>) TestFramework.factory()
        .getInstance(driverType);

    final var arguments = Optional.ofNullable(getArguments(factory))
      .orElse(Collections.emptyList());
    final var emulation = getEmulationModel(factory.driverName(), deviceType);
    final var binaryPath = getBinaryPath(factory);
    final var userAgent = getDesiredUserAgent(factory);

    factory.forDevice(deviceType)
      .withBinaryPath(binaryPath)
      .withUserAgent(userAgent)
      .withProxyType(proxyType)
      .withArguments(arguments)
      .withEmulation(emulation)
      .withOwner(Optional.ofNullable(config())
        .map(PlaywrightDriverProperties::getOwner)
        .orElse(System.getenv("user.name")))
      .headless(Optional.ofNullable(config())
        .map(PlaywrightDriverProperties::isHeadless)
        .orElse(false));

    BrowserType.LaunchOptions options = factory.defaultOptions();

    if (deviceType.equals(DeviceType.MOBILE)) {
      BrowserType.LaunchOptions mobileOptions = factory.mobileOptions();
      if (ObjectUtils.isNotEmpty(mobileOptions)) {
        options = mobileOptions;
      }
    }

    if (ObjectUtils.isNotEmpty(proxyType)) {
      BrowserType.LaunchOptions proxyOptions = factory.proxyOptions();
      if (ObjectUtils.isNotEmpty(proxyOptions)) {
        options = proxyOptions;
      }
    }

    final var remote = getRemoteDriverProperties(deviceType);
    boolean maximizeBrowser = Optional.ofNullable(config())
      .map(PlaywrightDriverProperties::isMaximizeBrowser)
      .orElse(false);
    boolean fixedLayoutViewport = deviceType.equals(DeviceType.MOBILE)
      || Optional.ofNullable(emulation)
        .map(EmulationModel::isAdjustDimension)
        .orElse(false);
    // Full-window chrome + a fixed Playwright viewport causes resize/flicker.
    if (maximizeBrowser && !fixedLayoutViewport) {
      List<String> args = options.args;
      args.add("--start-maximized");
      options.setArgs(args);

      log.debug("Using (maximize) browser size");
    }

    String resolvedUserAgent = resolveUserAgent(factory);
    String stealthScript = resolveStealthScript(factory);

    PlaywrightSession session = new PlaywrightSession();
    session.setMaximized(maximizeBrowser);

    // =========================
    // VIEWPORT / EMULATION CONFIG
    // =========================

    if (Optional.ofNullable(emulation)
      .map(EmulationModel::isAdjustDimension)
      .orElse(false)) {
      DeviceDimension dimension = factory.getDimension(emulation);

      double pixelRatio = dimension.getPixelRatio();
      boolean mobile = deviceType.equals(DeviceType.MOBILE);
      session.withViewportConfig(
        dimension.getWidth(),
        dimension.getHeight(),
        pixelRatio > 0 ? pixelRatio : null,
        mobile,
        mobile
      );

      log.debug(
        "Configured viewport {}x{} (scaleFactor={}, mobile={})",
        dimension.getWidth(),
        dimension.getHeight(),
        pixelRatio,
        mobile
      );

    } else if (deviceType.equals(DeviceType.MOBILE)) {

      DeviceDimension dimension = factory.getDimension(emulation);

      session.withViewportConfig(dimension.getWidth(), dimension.getHeight(), 2.0, true, true);

      log.debug("Mobile emulation with viewport {}x{}", dimension.getWidth(), dimension.getHeight());

    }

    BrowserType.LaunchOptions finalOptions = options;
    long initStartedAt = System.nanoTime();
    session.runInitializeBlocking(() -> {
      long playwrightCreateStartedAt = System.nanoTime();
      Playwright pw = Playwright.create();
      session.retainPlaywright(pw);
      long playwrightCreatedAt = System.nanoTime();

      BrowserType browserType = resolveBrowserType(pw, name);
      Browser browser;
      long browserLaunchStartedAt = System.nanoTime();
      if (Optional.ofNullable(remote)
        .map(RemoteDriverConfig::isEnabled)
        .orElse(false)) {
        final var url = Optional.of(remote)
          .map(RemoteDriverConfig::getUri)
          .filter(StringUtils::isNotBlank)
          .orElse(DEFAULT_REMOTE_URL);
        log.info(
          "#Connecting to remote playwright browser for {} in {} platform to {}",
          factory.driverName(),
          deviceType.name()
            .toLowerCase(),
          url
        );
        browser = browserType.connect(
          url,
          new BrowserType.ConnectOptions().setTimeout(remote.getConnectionTimeoutSeconds() * 1000)
        );
      } else {
        log.debug("Launching local playwright browser for {} in {} platform", factory.driverName(), deviceType.name());
        browser = browserType.launch(finalOptions);
      }
      long browserLaunchedAt = System.nanoTime();
      session.using(browser);
      session.withStealthConfig(resolvedUserAgent, stealthScript);
      session.on(deviceType);

      long initializedAt = System.nanoTime();
      log.info(
        "Playwright init timing [{}|{}]: create={}ms, launch/connect={}ms, context+page={}ms, total={}ms",
        factory.driverName(),
        deviceType.name().toLowerCase(),
        TimeUnit.NANOSECONDS.toMillis(playwrightCreatedAt - playwrightCreateStartedAt),
        TimeUnit.NANOSECONDS.toMillis(browserLaunchedAt - browserLaunchStartedAt),
        TimeUnit.NANOSECONDS.toMillis(initializedAt - browserLaunchedAt),
        TimeUnit.NANOSECONDS.toMillis(initializedAt - playwrightCreateStartedAt)
      );
    });
    long initFinishedAt = System.nanoTime();
    long totalInitMs = TimeUnit.NANOSECONDS.toMillis(initFinishedAt - initStartedAt);
    if (totalInitMs > 10_000) {
      log.warn("Playwright session initialization took {}ms (>{}ms threshold)", totalInitMs, 10_000);
    }
    return session;
  }

  private String resolveUserAgent(AbstractDriver<Browser, BrowserType.LaunchOptions> factory) {
    String configuredUA = factory.getUserAgent();
    if (StringUtils.isNotBlank(configuredUA)) {
      return configuredUA;
    }
    if (factory instanceof StealthProvider stealth) {
      return stealth.defaultUserAgent();
    }
    return null;
  }

  private String resolveStealthScript(AbstractDriver<Browser, BrowserType.LaunchOptions> factory) {
    if (factory instanceof StealthProvider stealth) {
      return stealth.stealthInitScript();
    }
    return null;
  }

  private static BrowserType resolveBrowserType(Playwright playwright, String name) {
    return switch (name.toLowerCase()
      .trim()) {
      case "firefox" -> playwright.firefox();
      case "webkit", "safari" -> playwright.webkit();
      default -> playwright.chromium();
    };
  }

  private EmulationModel getEmulationModel(String name, DeviceType deviceType) {
    return Optional.ofNullable(config())
      .map(PlaywrightDriverProperties::getEmulation)
      .map(emulation -> emulation.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .map(model -> model.get(name))
      .orElse(null);
  }

  private RemoteDriverConfig getRemoteDriverProperties(DeviceType deviceType) {
    return Optional.ofNullable(config())
      .map(PlaywrightDriverProperties::getRemoteDriver)
      .map(remote -> remote.get(Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT)))
      .orElse(null);
  }

  private String getBinaryPath(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(PlaywrightDriverProperties::getBinaryPath)
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
      .map(PlaywrightDriverProperties::getArgs)
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

  private String getDesiredUserAgent(AbstractDriver<?, ?> targetDriver) {
    return Optional.ofNullable(config())
      .map(PlaywrightDriverProperties::getUserAgent)
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

  @Override
  public Logger log() {
    return log;
  }
}
