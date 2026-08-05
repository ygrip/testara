package io.github.ygrip.testara.ui.vibium.engine;

import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.vibium.Browser;
import com.vibium.types.StartOptions;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.factory.EngineFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.vibium.config.VibiumDriverProperties;
import io.github.ygrip.testara.ui.vibium.config.VibiumRemoteConfig;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;

import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unchecked")
public final class VibiumEngine implements EngineFactory<VibiumDriverProperties> {
  private static final String ID = "vibium";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public VibiumDriverProperties config() {
    return TestFramework.configuration()
      .get(VibiumDriverProperties.class);
  }

  @Override
  public VibiumSession forDriver(String name) throws Exception {
    return forDriver(name, null);
  }

  @Override
  public VibiumSession forDriver(String name, String deviceType) throws Exception {
    return forDriver(name, deviceType, null);
  }

  @Override
  public VibiumSession forDriver(String name, String deviceType, String proxyType) throws Exception {
    final var type = deviceType(deviceType);
    final var proxy = proxyType(proxyType);
    final var driverFullName = constructDriverName(name, type, proxy);

    VibiumSession session = (VibiumSession) DriverSessionManager.inThisTestThread()
      .getDriver(driverFullName);

    if (ObjectUtils.isEmpty(session)) {
      session = createSession(name, type, proxy);
      DriverSessionManager.inThisTestThread()
        .registerDriver(driverFullName)
        .forDriver(session);
    }
    // Same thread as the test: always mark this session current so PageContext / Actor see it,
    // including cache hits (Selenium/Playwright engines use the same rule).
    DriverSessionManager.inThisTestThread()
      .setCurrentActiveDriver(session);

    return session;
  }

  @SuppressWarnings("unchecked")
  private VibiumSession createSession(String name, DeviceType deviceType, AvailableProxy proxyType) throws Exception {
    final var drivers = loadDrivers();
    final var driverType = drivers.get(name);

    AbstractDriver<Browser, StartOptions> factory =
      (AbstractDriver<Browser, StartOptions>) TestFramework.factory()
        .getInstance(driverType);

    factory.forDevice(deviceType)
      .withProxyType(proxyType)
      .withBinaryPath(Optional.ofNullable(config())
        .map(VibiumDriverProperties::getVibiumBinaryPath)
        .orElse(null))
      .withOwner(Optional.ofNullable(config())
        .map(VibiumDriverProperties::getOwner)
        .orElse(System.getenv("user.name")))
      .headless(Optional.ofNullable(config())
        .map(VibiumDriverProperties::isHeadless)
        .orElse(false));

    StartOptions options = factory.defaultOptions();

    if (deviceType.equals(DeviceType.MOBILE)) {
      StartOptions mobileOptions = factory.mobileOptions();
      if (ObjectUtils.isNotEmpty(mobileOptions)) {
        options = mobileOptions;
      }
    }

    if (ObjectUtils.isNotEmpty(proxyType)) {
      // Vibium's StartOptions has no proxy field; this always throws
      // UnsupportedVibiumCapabilityException before a browser is launched.
      options = factory.proxyOptions();
    }

    // Plan §14 "Remote connect": VibiumRemoteConfig has been declared on VibiumDriverProperties
    // since Phase 1, but nothing ever actually applied it to a live StartOptions instance until
    // now. Wired here rather than inside VibiumChromium because VibiumChromium's fluent builder
    // methods are all declared on the generic AbstractDriver<Browser, StartOptions> supertype
    // (losing the concrete Vibium subtype after the very first chained call), while `options` here
    // is already the concrete StartOptions this factory produced.
    VibiumRemoteConfig remoteConfig = Optional.ofNullable(config())
      .map(VibiumDriverProperties::getRemoteConnect)
      .orElse(null);
    boolean remoteConnected = remoteConfig != null
      && remoteConfig.isEnabled()
      && StringUtils.isNotBlank(remoteConfig.getUrl());
    if (remoteConnected) {
      options = options.connectURL(remoteConfig.getUrl());
      if (ObjectUtils.isNotEmpty(remoteConfig.getHeaders())) {
        options = options.connectHeaders(remoteConfig.getHeaders());
      }
    }

    log.debug("Launching local vibium browser for {} in {} platform", factory.driverName(), deviceType.name());
    Browser browser = factory.create(options);

    VibiumSession session = new VibiumSession();
    session.using(browser);
    session.markRemoteConnected(remoteConnected);
    session.withViewport(Optional.ofNullable(config())
      .map(VibiumDriverProperties::getViewport)
      .orElse(Collections.emptyMap()));
    session.on(deviceType);

    return session;
  }

  @Override
  public Logger log() {
    return log;
  }
}
