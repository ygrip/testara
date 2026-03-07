package io.github.ygrip.testara.properties.support;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.properties.model.ConsulProperties;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class ConsulHelper {

  private final ConsulClient client;
  private final String defaultPrefix;
  private final Map<String, String> mappedPrefixes;
  private final boolean enabled;
  private final String aclToken;

  public ConsulHelper(ConsulProperties properties) {
    if (ObjectUtils.isNotEmpty(properties)) {
      this.defaultPrefix = StringUtils.isBlank(properties.getPrefix()) ? "/" : normalize(properties.getPrefix());
      this.mappedPrefixes = ObjectUtils.isNotEmpty(properties.getMapped()) ? properties.getMapped() : new HashMap<>();
      this.enabled = properties.isEnabled() && StringUtils.isNotBlank(properties.getHost()) && ObjectUtils.isNotEmpty(
          properties.getPort());
      this.aclToken = properties.getAclToken();

      this.client = enabled ? new ConsulClient(properties.getHost(), properties.getPort()) : null;
    } else {
      this.defaultPrefix = "/";
      this.mappedPrefixes = new HashMap<>();
      this.enabled = false;
      this.aclToken = null;
      this.client = null;
    }
  }

  public boolean isEnabled(){
    return this.enabled;
  }

  /* =========================
     Public API
     ========================= */

  public ConsulClient client() {
    return client;
  }

  public String getValue(String key) {
    return getValues().get(key);
  }

  public String getValue(String prefix, String key) {
    return getValues(mappedPrefixes.getOrDefault(prefix, prefix)).get(key);
  }

  public Map<String, String> getValues() {
    return getValues(defaultPrefix);
  }

  public Map<String, String> getValues(String prefix) {
    Map<String, String> result = new HashMap<>();

    if (!enabled) {
      return result;
    }

    try {
      String normalized = normalize(prefix);

      if (isPrefix(normalized)) {
        loadMergedFromPrefix(normalized, result);
      } else {
        loadFromSingleValue(normalized, result);
      }
    } catch (Exception err) {
      log.warn("Fail to load configuration from vault", err);
    }

    return result;
  }

  /* =========================
     Detection
     ========================= */

  private boolean isPrefix(String prefix) {
    String path = prefix.endsWith("/") ? prefix : prefix + "/";

    Response<List<GetValue>> response =
        StringUtils.isBlank(aclToken) ? client.getKVValues(path) : client.getKVValues(path, aclToken);

    return response != null && response.getValue() != null && !response.getValue().isEmpty();
  }

  /* =========================
     Folder / merge handling
     ========================= */

  private void loadMergedFromPrefix(String prefix, Map<String, String> target) {
    String path = prefix.endsWith("/") ? prefix : prefix + "/";

    Response<List<GetValue>> response =
        StringUtils.isBlank(aclToken) ? client.getKVValues(path) : client.getKVValues(path, aclToken);

    if (response.getValue() == null) {
      return;
    }

    for (GetValue kv : response.getValue()) {
      if (kv.getDecodedValue() == null) {
        continue;
      }

      String relativeKey = kv.getKey().substring(path.length());

      // shallow merge only
      if (relativeKey.contains("/")) {
        continue;
      }

      mergeEntry(relativeKey, kv.getDecodedValue(), target);
    }
  }

  private void mergeEntry(String entryKey, String value, Map<String, String> target) {
    Properties props = new Properties();
    try {
      props.load(new StringReader(value));
      if (!props.isEmpty()) {
        props.forEach((k, v) -> {
          if (target.containsKey(String.valueOf(k))) {
            log.debug("Overriding key '{}' from entry '{}'", k, entryKey);
          }
          target.put(String.valueOf(k), String.valueOf(v));
        });
        return;
      }
    } catch (Exception ignored) {
      // fall through
    }

    // scalar fallback
    target.put(entryKey, value);
  }

  /* =========================
     Single value handling
     ========================= */

  private void loadFromSingleValue(String key, Map<String, String> target) {
    Response<GetValue> response =
        StringUtils.isBlank(aclToken) ? client.getKVValue(key) : client.getKVValue(key, aclToken);

    if (response.getValue() == null || response.getValue().getDecodedValue() == null) {
      return;
    }

    String rawValue = response.getValue().getDecodedValue();

    Properties props = new Properties();
    try {
      props.load(new StringReader(rawValue));
      if (!props.isEmpty()) {
        props.forEach((k, v) -> target.put(String.valueOf(k), String.valueOf(v)));
        return;
      }
    } catch (Exception ignored) {
      // fall through
    }

    // raw scalar fallback
    target.put(extractLeafKey(key), rawValue);

    log.debug("Consul key '{}' is not a valid .properties blob; loaded as raw value", key);
  }

  /* =========================
     Helpers
     ========================= */

  private String extractLeafKey(String fullKey) {
    if (fullKey == null || !fullKey.contains("/")) {
      return fullKey;
    }
    return fullKey.substring(fullKey.lastIndexOf('/') + 1);
  }

  private String normalize(String path) {
    if (path == null) {
      return "/";
    }
    if (path.equals("/")) {
      return "/";
    }
    return path.startsWith("/") ? path.substring(1) : path;
  }
}

