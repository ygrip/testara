package io.github.ygrip.testara.ui.appium.driver;

import java.util.List;

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

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

@Log4j2
@DriverMetadata(name = "android",
  engine = AppiumEngine.class,
  platforms = {DeviceType.ANDROID}
)
public class Android extends AbstractDriver<AndroidDriver, UiAutomator2Options> {

  @Override
  public AndroidDriver create(UiAutomator2Options options) {
    return new AndroidDriver(options);
  }

  @Override
  public UiAutomator2Options proxyOptions() {
    UiAutomator2Options options = defaultOptions();
    if (getProxyType() != null) {
      Proxy proxy = ProxyAutomationRegistry.forProxy(AppiumProxy.class)
        .create(getProxyType());
      if (ObjectUtils.isNotEmpty(proxy)) {
        options.setCapability("proxy", proxy);
        log.debug("Android proxy configured via capability: {}", proxy.getHttpProxy());
      }
    }
    return options;
  }

  @Override
  public UiAutomator2Options mobileOptions() {
    return defaultOptions();
  }

  @Override
  public UiAutomator2Options defaultOptions() {
    UiAutomator2Options options = new UiAutomator2Options();
    List<String> additionalArgs = getArguments();
    if (ObjectUtils.isNotEmpty(additionalArgs)) {
      options.setAvdArgs(additionalArgs);
    }
    options.ignoreHiddenApiPolicyError();
    options.setEnforceAppInstall(true);
    options.autoGrantPermissions();
    options.allowTestPackages();
    options.setPlatformName(Platform.ANDROID.name());
    return options;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
