package io.github.ygrip.testara.ui.playwright.proxy;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.ui.factory.ProxyFactory;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.proxy.AbstractProxy;
import io.github.ygrip.testara.ui.proxy.ProxyInstanceManager;

/**
 * Produces a proxy address string for Playwright driver options.
 * <p>
 * For {@link AvailableProxy#MITMPROXY}, delegates to {@link MitmProxyPlaywrightUtility}
 * which connects to the MitmProxy control plane and returns an address that Playwright
 * drivers can use with {@link com.microsoft.playwright.options.Proxy}.
 */
public class PlaywrightProxy implements ProxyFactory<String> {

  @Override
  public String create(AvailableProxy proxyType) {
    if (proxyType == null) {
      return null;
    }

    if (proxyType == AvailableProxy.MITMPROXY) {
      return createMitmProxy();
    }

    // STANDALONE / EMBEDDED not natively supported in Playwright;
    // return null so the driver proceeds without proxy config.
    return null;
  }

  @SuppressWarnings("rawtypes")
  private String createMitmProxy() {
    AbstractProxy existing = ProxyInstanceManager.currentProxy();
    if (existing instanceof MitmProxyPlaywrightUtility reusable && reusable.isStarted()) {
      RootRegistry.instance().registerOverride(reusable, RegistryScope.TEST);
      return reusable.getProxy();
    }

    MitmProxyPlaywrightUtility proxyUtil = getOrCreateMitmProxyUtility();
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

  private static MitmProxyPlaywrightUtility getOrCreateMitmProxyUtility() {
    try {
      MitmProxyPlaywrightUtility existing =
          TestFramework.context().get(MitmProxyPlaywrightUtility.class);
      if (existing != null) {
        return existing;
      }
    } catch (Exception ignored) { }
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    return new MitmProxyPlaywrightUtility(dataHolder);
  }
}
