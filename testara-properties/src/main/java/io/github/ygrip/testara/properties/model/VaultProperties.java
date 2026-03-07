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
@LoadProperties(prefix = "config.vault")
public class VaultProperties {
  private boolean enabled;
  private String address;
  private String token;
  private String path;
  private Integer engineVersion;
  private Map<String, String> mapped;
}
