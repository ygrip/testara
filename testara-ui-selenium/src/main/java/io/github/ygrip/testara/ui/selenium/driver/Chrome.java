package io.github.ygrip.testara.ui.selenium.driver;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.registry.ProxyAutomationRegistry;
import io.github.ygrip.testara.ui.selenium.engine.SeleniumEngine;
import io.github.ygrip.testara.ui.selenium.proxy.SeleniumProxy;

@DriverMetadata(name = "chrome",
  engine = SeleniumEngine.class,
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class Chrome extends AbstractDriver<ChromeDriver, ChromeOptions> {

  @Override
  public ChromeDriver create(ChromeOptions options) {
    return new ChromeDriver(options);
  }

  @Override
  public ChromeOptions proxyOptions() {
    ChromeOptions options = new ChromeOptions();
    Proxy proxy = ProxyAutomationRegistry.forProxy(SeleniumProxy.class)
      .create(getProxyType());
    if (ObjectUtils.isNotEmpty(proxy)) {
      options.setProxy(proxy);
      options.setAcceptInsecureCerts(true);
    }
    return options;
  }

  @Override
  public ChromeOptions mobileOptions() {
    ChromeOptions options = defaultOptions();
    options.setExperimentalOption("mobileEmulation", getDeviceMetrics());
    return options;
  }

  @Override
  public ChromeOptions defaultOptions() {
    Map<String, Object> chromePrefs = new HashMap<>();
    chromePrefs.put("profile.default_content_settings.popups", 0);
    chromePrefs.put("download.prompt_for_download", false);
    chromePrefs.put("credentials_enable_service", false);
    chromePrefs.put("profile.password_manager_enabled", false);
    chromePrefs.put("profile.password_manager_leak_detection", false);
    chromePrefs.put("download.default_directory", getDownloadLocation());
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--disable-extensions");
    options.addArguments("--ignore-certificate-errors");
    options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
    options.setExperimentalOption("prefs", chromePrefs);
    if (isJavaScriptEnabled()) {
      options.addArguments("--enable-javascript");
    }
    if (isHeadless()) {
      options.addArguments("--disable-setuid-sandbox");
      options.addArguments("--headless");
    }
    final var binaryPath = getBinaryPath();
    if (StringUtils.isNotBlank(binaryPath)) {
      options.setBinary(binaryPath);
    }
    options.addArguments("--enable-bidi");
    options.addArguments("--disable-web-security");
    options.addArguments("--disable-features=VizDisplayCompositor");
    options.addArguments("--disable-gpu");
    options.addArguments("--no-sandbox");
    List<String> additionalArgs = getArguments();
    if (ObjectUtils.isNotEmpty(additionalArgs)) {
      options.addArguments(additionalArgs);
    }

    options.setAcceptInsecureCerts(true);
    return options;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
