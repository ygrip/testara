package io.github.ygrip.testara.ui.selenium.proxy;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.ui.factory.ProxyFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.proxy.AbstractProxy;

/**
 * Produces Selenium {@link Proxy} for driver options by delegating to the appropriate
 * {@link AbstractProxy} implementation based on proxy type:
 * <ul>
 *   <li>{@link AvailableProxy#STANDALONE} / {@link AvailableProxy#EMBEDDED} →
 *       {@link BrowserUpProxyUtility}</li>
 *   <li>{@link AvailableProxy#MITMPROXY} →
 *       {@link MitmProxySeleniumUtility}</li>
 * </ul>
 */
public class SeleniumProxy implements ProxyFactory<Proxy> {

  @Override
  public Proxy create(AvailableProxy proxyType) {
    if (proxyType == null) {
      return null;
    }

    if (proxyType == AvailableProxy.MITMPROXY) {
      return createMitmProxy();
    }

    return createBrowserUpProxy(proxyType);
  }

  private Proxy createBrowserUpProxy(AvailableProxy proxyType) {
    AbstractProxy<Proxy> proxyUtil = getOrCreateBrowserUpUtility();
    proxyUtil.setProxyType(proxyType);
    if (proxyType == AvailableProxy.STANDALONE) {
      String url = config().getStandaloneUrl();
      if (StringUtils.isNotBlank(url)) {
        proxyUtil.setRemoteProxyAddress(url.trim());
      }
    }
    proxyUtil.start();
    return proxyUtil.getProxy();
  }

  private Proxy createMitmProxy() {
    MitmProxySeleniumUtility proxyUtil = getOrCreateMitmProxyUtility();
    proxyUtil.setProxyType(AvailableProxy.MITMPROXY);

    String apiUrl = config().getMitmproxyApiUrl();
    if (StringUtils.isNotBlank(apiUrl)) {
      proxyUtil.setMitmProxyApiUrl(apiUrl.trim());
      proxyUtil.setRemoteProxyAddress(apiUrl.trim());
    }
    proxyUtil.start();
    return proxyUtil.getProxy();
  }

  private static AbstractProxy<Proxy> getOrCreateBrowserUpUtility() {
    try {
      BrowserUpProxyUtility existing = TestFramework.context().get(BrowserUpProxyUtility.class);
      if (existing != null) {
        return existing;
      }
    } catch (Exception ignored) { }
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    return new BrowserUpProxyUtility(dataHolder);
  }

  private static MitmProxySeleniumUtility getOrCreateMitmProxyUtility() {
    try {
      MitmProxySeleniumUtility existing = TestFramework.context().get(MitmProxySeleniumUtility.class);
      if (existing != null) {
        return existing;
      }
    } catch (Exception ignored) { }
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    return new MitmProxySeleniumUtility(dataHolder);
  }
}
