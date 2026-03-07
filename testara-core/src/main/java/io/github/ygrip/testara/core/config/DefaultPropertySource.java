package io.github.ygrip.testara.core.config;

import java.util.HashMap;
import java.util.Map;

public final class DefaultPropertySource implements PropertySource {
  @Override
  public int priority() {
    return 2;
  }

  @Override
  public Map<String, String> load(Map<String, String> properties) {
    Map<String, String> systemProperties = new HashMap<>();
    System.getProperties().forEach((k, v) -> systemProperties.put(String.valueOf(k), String.valueOf(v)));
    return combine(systemProperties, properties);
  }
}
