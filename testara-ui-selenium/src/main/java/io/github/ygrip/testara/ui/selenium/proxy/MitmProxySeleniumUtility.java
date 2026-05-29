package io.github.ygrip.testara.ui.selenium.proxy;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.proxy.AbstractMitmProxyUtility;
import org.openqa.selenium.Proxy;

import lombok.extern.log4j.Log4j2;

/**
 * MitmProxy Grid proxy utility for Selenium WebDriver.
 * Creates a Selenium {@link Proxy} pointing at the mitmproxy instance port.
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class MitmProxySeleniumUtility extends AbstractMitmProxyUtility<Proxy> {

  public MitmProxySeleniumUtility(DataHolder dataHolder) {
    super(dataHolder);
  }

  @Override
  protected Proxy buildProxy(String proxyHost, int port) {
    String addr = proxyHost + ":" + port;
    Proxy proxy = new Proxy();
    proxy.setHttpProxy(addr);
    proxy.setSslProxy(addr);
    proxy.setFtpProxy(addr);
    log.info("#MitmProxy Selenium proxy started at {}", addr);
    return proxy;
  }
}
