package io.github.ygrip.testara.ui.appium.proxy;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.ui.factory.ProxyFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.proxy.AbstractProxy;
import io.github.ygrip.testara.ui.proxy.ProxyInstanceManager;
import io.github.ygrip.testara.ui.selenium.proxy.SeleniumProxy;

/**
 * Produces Selenium {@link Proxy} for Appium driver capabilities.
 * <p>
 * For {@link AvailableProxy#MITMPROXY}, delegates to {@link MitmProxyAppiumUtility}.
 * For {@link AvailableProxy#STANDALONE} / {@link AvailableProxy#EMBEDDED}, falls back to
 * {@link SeleniumProxy} since Appium uses Selenium under the hood.
 */
public class AppiumProxy implements ProxyFactory<Proxy> {

  private final SeleniumProxy seleniumProxy = new SeleniumProxy();

  @Override
  public Proxy create(AvailableProxy proxyType) {
    if (proxyType == null) {
      return null;
    }

    if (proxyType == AvailableProxy.MITMPROXY) {
      return createMitmProxy();
    }

    return seleniumProxy.create(proxyType);
  }

  @SuppressWarnings("rawtypes")
  private Proxy createMitmProxy() {
    AbstractProxy existing = ProxyInstanceManager.currentProxy();
    if (existing instanceof MitmProxyAppiumUtility reusable && reusable.isStarted()) {
      RootRegistry.instance().registerOverride(reusable, RegistryScope.TEST);
      return reusable.getProxy();
    }

    MitmProxyAppiumUtility proxyUtil = getOrCreateMitmProxyUtility();
    proxyUtil.setProxyType(AvailableProxy.MITMPROXY);

    String apiUrl = config().getMitmproxyApiUrl();
    if (StringUtils.isNotBlank(apiUrl)) {
      proxyUtil.setMitmProxyApiUrl(apiUrl.trim());
      proxyUtil.setRemoteProxyAddress(apiUrl.trim());
    }
    proxyUtil.start();

    ProxyInstanceManager.setCurrentProxy(proxyUtil);
    RootRegistry.instance().registerOverride(proxyUtil, RegistryScope.TEST);

    return proxyUtil.getProxy();
  }

  private static MitmProxyAppiumUtility getOrCreateMitmProxyUtility() {
    try {
      MitmProxyAppiumUtility existing =
          TestFramework.context().get(MitmProxyAppiumUtility.class);
      if (existing != null) {
        return existing;
      }
    } catch (Exception ignored) { }
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    return new MitmProxyAppiumUtility(dataHolder);
  }
}
