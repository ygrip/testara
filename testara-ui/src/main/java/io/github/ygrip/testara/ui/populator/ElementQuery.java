package io.github.ygrip.testara.ui.populator;

import java.util.Map;

public record ElementQuery(
    String name,
    Map<String, ?> parameters
) {

  public static ElementQuery of(String name) {
    return new ElementQuery(name, Map.of());
  }

  public static ElementQuery of(String name, Map<String, ?> parameters) {
    if (parameters == null) {
      return new ElementQuery(name, Map.of());
    }
    return new ElementQuery(name, parameters);
  }

  public boolean hasParameters() {
    return parameters != null && !parameters.isEmpty();
  }
}
