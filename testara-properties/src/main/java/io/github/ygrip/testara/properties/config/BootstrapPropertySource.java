package io.github.ygrip.testara.properties.config;

import io.github.ygrip.testara.core.config.PlaceholderResolver;
import io.github.ygrip.testara.core.model.PlaceholderLookup;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public abstract class BootstrapPropertySource {

  public String resolve(String value, PlaceholderLookup lookup) {
    return PlaceholderResolver.resolve(value, lookup);
  }

  public String get(String key, Map<String, String> properties) {
    return properties.getOrDefault(key, System.getProperty(key));
  }

  public String require(String key, Map<String, String> properties) {
    if (properties.containsKey(key)) {
      return properties.get(key);
    }
    String value = System.getProperty(key);
    if (StringUtils.isBlank(value)) {
      throw new IllegalStateException("Missing bootstrap property: " + key);
    }
    return value;
  }
}
