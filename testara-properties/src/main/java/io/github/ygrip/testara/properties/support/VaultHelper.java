package io.github.ygrip.testara.properties.support;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.properties.model.VaultProperties;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class VaultHelper {
  private final Vault client;
  private final String path;
  private final boolean enabled;
  private final Map<String, String> mappedPrefixes;

  public VaultHelper(VaultProperties properties) {
    boolean enabled;
    mappedPrefixes = new HashMap<>();
    if (ObjectUtils.isNotEmpty(properties)) {
      path = StringUtils.isBlank(properties.getPath()) ? "/" : properties.getPath();
      enabled = properties.isEnabled() && StringUtils.isNotBlank(properties.getAddress()) && ObjectUtils.isNotEmpty(
          properties.getToken());
      if (enabled) {
        if (ObjectUtils.isNotEmpty(properties.getMapped())) {
          mappedPrefixes.putAll(properties.getMapped());
        }
        VaultConfig config = null;
        try {
          config = new VaultConfig().address(properties.getAddress())
              .token(properties.getToken())
              .engineVersion(properties.getEngineVersion())
              .build();
        } catch (Exception err) {
          log.warn("Fail to establish connection to vault", err);
          enabled = false;
        }
        if (ObjectUtils.isNotEmpty(config)) {
          client = new Vault(config);
        } else {
          client = null;
        }
      } else {
        client = null;
      }
    } else {
      path = "/";
      enabled = false;
      client = null;
    }
    this.enabled = enabled;
  }

  public boolean isEnabled(){
    return this.enabled;
  }

  public Vault client() {
    return this.client;
  }

  public String getValue(String key) {
    return getValues().get(key);
  }

  public String getValue(String path, String key) {
    return getValues(mappedPrefixes.getOrDefault(path, path)).get(key);
  }

  public Map<String, String> getValues() {
    return getValues(path);
  }

  public Map<String, String> getValues(String path) {
    Map<String, String> properties = new HashMap<>();
    if (enabled) {
      try {
        properties.putAll(client.logical().read(path).getData());
      } catch (Exception err) {
        log.warn("Fail to load configuration from vault", err);
      }
    }

    return properties;
  }
}
