package io.github.ygrip.testara.core.config;

import java.util.HashMap;
import java.util.Map;

public final class EnvironmentPropertySource implements PropertySource {
  @Override
  public int priority() {
    return 1;
  }

  @Override
  public Map<String, String> load(Map<String, String> properties) {
    Map<String, String> envProperties = new HashMap<>();
    System.getenv().forEach(envProperties::putIfAbsent);
    return combine(envProperties, properties);
  }
}
