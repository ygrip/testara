package io.github.ygrip.testara.ui.appium.driver;

import org.apache.commons.lang3.ObjectUtils;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;

import io.github.ygrip.testara.ui.appium.engine.AppiumEngine;
import io.github.ygrip.testara.ui.appium.proxy.AppiumProxy;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;

import io.github.ygrip.testara.ui.registry.ProxyAutomationRegistry;
import lombok.extern.log4j.Log4j2;

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

@Log4j2
@DriverMetadata(name = "ios",
  engine = AppiumEngine.class,
  platforms = {DeviceType.IOS}
)
public class IOS extends AbstractDriver<IOSDriver, XCUITestOptions> {

  @Override
  public IOSDriver create(XCUITestOptions options) {
    return new IOSDriver(options);
  }

  @Override
  public XCUITestOptions proxyOptions() {
    XCUITestOptions options = defaultOptions();
    if (getProxyType() != null) {
      Proxy proxy = ProxyAutomationRegistry.forProxy(AppiumProxy.class)
        .create(getProxyType());
      if (ObjectUtils.isNotEmpty(proxy)) {
        options.setCapability("proxy", proxy);
        log.debug("iOS proxy configured via capability: {}", proxy.getHttpProxy());
      }
    }
    return options;
  }

  @Override
  public XCUITestOptions mobileOptions() {
    return defaultOptions();
  }

  @Override
  public XCUITestOptions defaultOptions() {
    XCUITestOptions options = new XCUITestOptions();
    options.setEnforceAppInstall(true);
    options.setShouldTerminateApp(true);
    options.safariIgnoreFraudWarning();
    options.allowProvisioningDeviceRegistration();
    options.setForceAppLaunch(true);
    options.setPlatformName(Platform.IOS.name());
    return options;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
