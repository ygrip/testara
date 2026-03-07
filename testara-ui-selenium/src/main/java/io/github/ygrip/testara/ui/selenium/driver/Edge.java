package io.github.ygrip.testara.ui.selenium.driver;

import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.selenium.engine.SeleniumEngine;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.selenium.proxy.SeleniumProxy;

import lombok.extern.log4j.Log4j2;

@Log4j2
@DriverMetadata(name = "edge",
  engine = SeleniumEngine.class,
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class Edge extends AbstractDriver<EdgeDriver, EdgeOptions> {

  @Override
  public EdgeDriver create(EdgeOptions options) {
    return new EdgeDriver(options);
  }

  @Override
  public EdgeOptions proxyOptions() {
    EdgeOptions options = new EdgeOptions();
    Proxy proxy = TestFramework.context()
      .get(SeleniumProxy.class)
      .create(getProxyType());
    if (ObjectUtils.isNotEmpty(proxy)) {
      options.setProxy(proxy);
      options.setAcceptInsecureCerts(true);
    }
    return options;
  }

  @Override
  public EdgeOptions mobileOptions() {
    EdgeOptions options = defaultOptions();
    options.setExperimentalOption("mobileEmulation", getDeviceMetrics());
    return options;
  }

  @Override
  public EdgeOptions defaultOptions() {
    EdgeOptions options = new EdgeOptions();

    options.addArguments("--ignore-certificate-errors");
    options.addArguments("disable-gpu");
    if (isJavaScriptEnabled()) {
      options.addArguments("--enable-javascript");
    }
    if (isHeadless()) {
      options.addArguments("--no-sandbox");
      options.addArguments("--headless");
    }
    List<String> additionalArgs = getArguments();
    if (ObjectUtils.isNotEmpty(additionalArgs)) {
      options.addArguments(additionalArgs);
    }
    final var binaryPath = getBinaryPath();
    if(StringUtils.isNotBlank(binaryPath)){
      options.setBinary(binaryPath);
    }
    return options;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
