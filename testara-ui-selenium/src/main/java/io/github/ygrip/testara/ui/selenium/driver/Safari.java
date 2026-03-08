package io.github.ygrip.testara.ui.selenium.driver;

import org.apache.commons.lang3.ObjectUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.registry.ProxyAutomationRegistry;
import io.github.ygrip.testara.ui.selenium.engine.SeleniumEngine;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.selenium.proxy.SeleniumProxy;

import lombok.extern.log4j.Log4j2;

@Log4j2
@DriverMetadata(name = "safari",
  engine = SeleniumEngine.class,
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class Safari extends AbstractDriver<SafariDriver, SafariOptions> {
  @Override
  public SafariDriver create(SafariOptions options) {
    return new SafariDriver(options);
  }

  @Override
  public SafariOptions proxyOptions() {
    SafariOptions options = new SafariOptions();
    Proxy proxy = ProxyAutomationRegistry.forProxy(SeleniumProxy.class)
      .create(getProxyType());
    if (ObjectUtils.isNotEmpty(proxy)) {
      options.setProxy(proxy);
      options.setAcceptInsecureCerts(true);
    }
    return options;
  }

  @Override
  public SafariOptions mobileOptions() {
    DesiredCapabilities capabilities = new DesiredCapabilities();
    return defaultOptions().merge(capabilities);
  }

  @Override
  public SafariOptions defaultOptions() {
    SafariOptions capabilities = new SafariOptions();
    capabilities.setCapability("safari.options.dataDir", getDownloadLocation());
    capabilities.setAutomaticProfiling(true);
    capabilities.setUseTechnologyPreview(true);
    if (isHeadless()) {
      log.warn("Safari web driver does not support headless mode");
    }
    return capabilities;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
