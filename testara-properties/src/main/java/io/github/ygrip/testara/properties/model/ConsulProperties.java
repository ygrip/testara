package io.github.ygrip.testara.properties.model;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "config.consul")
public class ConsulProperties {
  private boolean enabled;
  private String host;
  private Integer port;
  private String prefix;
  private String aclToken;
  private Map<String, String> mapped;
}
