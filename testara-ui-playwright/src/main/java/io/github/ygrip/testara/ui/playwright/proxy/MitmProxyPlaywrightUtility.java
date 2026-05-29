package io.github.ygrip.testara.ui.playwright.proxy;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.proxy.AbstractMitmProxyUtility;

import lombok.extern.log4j.Log4j2;

/**
 * MitmProxy Grid proxy utility for Playwright.
 * Returns a proxy URL string ({@code http://host:port}) that Playwright launch options accept.
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class MitmProxyPlaywrightUtility extends AbstractMitmProxyUtility<String> {

  public MitmProxyPlaywrightUtility(DataHolder dataHolder) {
    super(dataHolder);
  }

  @Override
  protected String buildProxy(String proxyHost, int port) {
    String addr = "http://" + proxyHost + ":" + port;
    log.info("#MitmProxy Playwright proxy started at {}", addr);
    return addr;
  }
}
