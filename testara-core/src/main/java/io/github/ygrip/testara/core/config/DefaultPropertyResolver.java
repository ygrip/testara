package io.github.ygrip.testara.core.config;

import io.github.ygrip.testara.core.model.PlaceholderLookup;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultPropertyResolver implements PropertyResolver {

  private final Map<String, PropertyValue> properties;
  private final PropertyNameStrategy nameStrategy;
  private final PlaceholderLookup lookup;

  public DefaultPropertyResolver() {
    this.nameStrategy = PropertyNameStrategyLoader.load();
    this.properties = new ConcurrentHashMap<>();
    this.lookup = key -> {
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
    this.properties.putAll(properties());
  }

  // ------------------------------------------------------------
  // Internal helpers
  // ------------------------------------------------------------

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(properties.get(nameStrategy.normalize(key)))
        .flatMap(val -> Optional.ofNullable(val.value()).map(Object::toString));
  }

  @Override
  public String get(String key, String fallback) {
    return get(key).orElse(fallback);
  }

  @Override
  public boolean contains(String key) {
    return properties.containsKey(nameStrategy.normalize(key));
  }

  @Override
  public PropertyNameStrategy strategy() {
    return nameStrategy;
  }

  // ------------------------------------------------------------
  // Normalization
  // ------------------------------------------------------------

  @Override
  public Map<String, PropertyValue> properties() {
    if (ObjectUtils.isNotEmpty(properties)) {
      return properties;
    }
    return load();
  }

  private Map<String, PropertyValue> load() {
    Map<String, PropertyValue> normalized = new ConcurrentHashMap<>();
    sourceProperties().forEach((k, v) -> {
      String resolved = PlaceholderResolver.resolve(v, lookup);
      normalized.put(nameStrategy.normalize(k), toValue(k, resolved));
    });
    return normalized;
  }

  @Override
  public void reload() {
    properties.putAll(load());
  }
}

