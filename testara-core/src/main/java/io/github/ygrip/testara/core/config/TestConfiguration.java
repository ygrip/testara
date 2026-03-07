package io.github.ygrip.testara.core.config;

import java.util.Map;

public interface TestConfiguration extends PropertyResolver {
  Map<String, PropertyValue> getByPrefix(String prefix);

  <T> T get(String prefix, Class<T> type);

  <T> T get(Class<T> type);
}
