package io.github.ygrip.testara.properties.vault;

import io.github.ygrip.testara.core.config.PropertySource;
import io.github.ygrip.testara.core.model.PlaceholderLookup;
import io.github.ygrip.testara.properties.config.BootstrapPropertySource;
import io.github.ygrip.testara.properties.model.VaultProperties;
import io.github.ygrip.testara.properties.support.VaultHelper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log4j2
public final class VaultPropertySource extends BootstrapPropertySource implements PropertySource {
  private final PlaceholderLookup LOOKUP;
  private volatile static VaultHelper vault;

  public VaultPropertySource() {
    LOOKUP = key -> {
      String result = null;
      String v = System.getenv(key);
      if (v != null) {
        result = v;
      } else {
        v = System.getProperty(key);
        if (v != null) {
          result = v;
        }
      }
      return result;
    };
  }

  VaultHelper getVault(Map<String, String> properties) {
    if (ObjectUtils.isEmpty(vault)) {
      boolean enabled = Boolean.parseBoolean(resolve(get("config.vault.enabled", properties), LOOKUP));

      if (enabled) {
        String address = resolve(require("config.vault.address", properties), LOOKUP);
        String token = resolve(require("config.vault.token", properties), LOOKUP);
        String path = resolve(require("config.vault.path", properties), LOOKUP);
        Integer version =
            Integer.parseInt(Optional.ofNullable(resolve(get("config.vault.engine-version", properties), LOOKUP))
                .orElse("2"));
        vault = new VaultHelper(VaultProperties.builder()
            .mapped(new HashMap<>())
            .engineVersion(version)
            .address(address)
            .enabled(true)
            .token(token)
            .path(path)
            .build());
      } else {
        vault = new VaultHelper(VaultProperties.builder().enabled(false).build());
      }
    }

    return vault;
  }

  @Override
  public int priority() {
    return 3;
  }

  @Override
  public Map<String, String> load(Map<String, String> properties) {
    Map<String, String> vaultProperties = getVault(properties).getValues();
    return combine(vaultProperties, properties);
  }
}
