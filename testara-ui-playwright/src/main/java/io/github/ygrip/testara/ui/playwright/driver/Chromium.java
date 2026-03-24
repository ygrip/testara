package io.github.ygrip.testara.ui.playwright.driver;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.playwright.engine.PlaywrightEngine;
import io.github.ygrip.testara.ui.playwright.proxy.PlaywrightProxy;
import io.github.ygrip.testara.ui.registry.ProxyAutomationRegistry;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.Proxy;

@DriverMetadata(name = "chrome",
  browserName = "chromium",
  engine = PlaywrightEngine.class,
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class Chromium extends AbstractDriver<Browser, BrowserType.LaunchOptions> implements StealthProvider {

  @Override
  public Browser create(BrowserType.LaunchOptions options) {
    throw new UnsupportedOperationException("Use PlaywrightEngine to create browser instances");
  }

  @Override
  public BrowserType.LaunchOptions proxyOptions() {
    BrowserType.LaunchOptions options = defaultOptions();
    if (getProxyType() == AvailableProxy.MITMPROXY) {
      String proxyAddr = ProxyAutomationRegistry.forProxy(PlaywrightProxy.class)
        .create(getProxyType());
      if (StringUtils.isNotBlank(proxyAddr)) {
        options.setProxy(new Proxy(proxyAddr));
      }
    }
    return options;
  }

  @Override
  public BrowserType.LaunchOptions mobileOptions() {
    return defaultOptions();
  }

  @Override
  public BrowserType.LaunchOptions defaultOptions() {
    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions();
    options.setHeadless(isHeadless());

    List<String> args = new ArrayList<>();
    args.add("--disable-blink-features=AutomationControlled");
    args.add("--disable-features=PasswordLeakDetection");
    args.add("--disable-save-password-bubble");
    args.add("--ignore-certificate-errors");
    args.add("--no-sandbox");

    List<String> additionalArgs = getArguments();
    if (ObjectUtils.isNotEmpty(additionalArgs)) {
      args.addAll(additionalArgs);
    }
    options.setArgs(args);

    final var binaryPath = getBinaryPath();
    if (StringUtils.isNotBlank(binaryPath)) {
      options.setExecutablePath(Paths.get(binaryPath));
    }

    return options;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }

  @Override
  public String stealthInitScript() {
    return """
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
      Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
      Object.defineProperty(navigator, 'plugins', {
        get: () => [1, 2, 3, 4, 5]
      });
      window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){} };
      const originalQuery = window.navigator.permissions.query;
      window.navigator.permissions.query = (parameters) =>
        parameters.name === 'notifications'
          ? Promise.resolve({ state: Notification.permission })
          : originalQuery(parameters);
      """;
  }

  @Override
  public String defaultUserAgent() {
    return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
  }
}
