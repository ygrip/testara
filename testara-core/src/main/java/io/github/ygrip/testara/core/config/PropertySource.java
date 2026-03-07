package io.github.ygrip.testara.core.config;

import java.util.HashMap;
import java.util.Map;

public interface PropertySource {
  default int priority() {
    return 0;
  }

  Map<String, String> load(Map<String, String> properties);

  default Map<String, String> combine(Map<String, String> higherPrecedence, Map<String, String> lowerPrecedence) {

    Map<String, String> result = new HashMap<>();

    if (lowerPrecedence != null) {
      result.putAll(lowerPrecedence);
    }

    if (higherPrecedence != null) {
      result.putAll(higherPrecedence);
    }

    return result;
  }

}
