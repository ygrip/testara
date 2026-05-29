package io.github.ygrip.testara.ui.appium.proxy;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.proxy.AbstractMitmProxyUtility;
import org.openqa.selenium.Proxy;

import lombok.extern.log4j.Log4j2;

/**
 * MitmProxy Grid proxy utility for Appium (Android / iOS).
 * Creates a Selenium {@link Proxy} that Appium capability options can reference.
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class MitmProxyAppiumUtility extends AbstractMitmProxyUtility<Proxy> {

  public MitmProxyAppiumUtility(DataHolder dataHolder) {
    super(dataHolder);
  }

  @Override
  protected Proxy buildProxy(String proxyHost, int port) {
    String addr = proxyHost + ":" + port;
    Proxy proxy = new Proxy();
    proxy.setHttpProxy(addr);
    proxy.setSslProxy(addr);
    log.info("#MitmProxy Appium proxy started at {}", addr);
    return proxy;
  }
}
