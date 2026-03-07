package io.github.ygrip.testara.ui.config;

import io.github.ygrip.testara.core.config.LoadProperties;

import lombok.Data;

@Data
@LoadProperties(prefix = "proxy")
public class ProxyProperties {
  private String standaloneUrl;
  private String mitmproxyApiUrl;
}
