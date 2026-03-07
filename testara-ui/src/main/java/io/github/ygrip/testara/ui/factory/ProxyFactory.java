package io.github.ygrip.testara.ui.factory;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.config.ProxyProperties;
import io.github.ygrip.testara.ui.model.AvailableProxy;

public interface ProxyFactory<T> {

  T create(AvailableProxy proxyType);

  default ProxyProperties config() {
    return TestFramework.configuration()
      .get(ProxyProperties.class);
  }
}
