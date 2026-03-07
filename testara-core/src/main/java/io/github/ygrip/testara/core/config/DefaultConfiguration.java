package io.github.ygrip.testara.core.config;

import io.github.ygrip.testara.core.error.InvalidConfigurationPropertiesException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultConfiguration implements TestConfiguration {
  private final PropertyResolver resolver;
  private final ConcurrentHashMap<Class<?>, Object> cachedConfiguration;

  public DefaultConfiguration() {
    this.resolver = PropertyResolverLoader.load();
    this.cachedConfiguration = new ConcurrentHashMap<>();
  }

  @Override
  public Map<String, PropertyValue> getByPrefix(String prefix) {
    String normalizedPrefix = resolver.strategy().normalize(prefix);
    String prefixWithDot = normalizedPrefix + ".";

    Map<String, PropertyValue> result = new LinkedHashMap<>();

    resolver.properties().forEach((key, value) -> {
      String normalizedKey;
      if (key.startsWith(prefixWithDot)) {
        normalizedKey = key.substring(prefixWithDot.length());
      } else if (key.startsWith(normalizedPrefix + "[")) {
        // e.g. "my.map.key1[0]" under prefix "my.map.key1" -> suffix "[0]"
        normalizedKey = key.substring(normalizedPrefix.length());
      } else {
        return;
      }

      Optional<String> originalKey = extractKey(value.key(), prefix);
      result.put(originalKey.orElse(normalizedKey), value);
    });

    return result;
  }

  Optional<String> extractKey(String originalKey, String rawPrefix) {
    if (originalKey == null || rawPrefix == null) {
      return Optional.empty();
    }

    // Step 1: normalize for comparison only
    String normalizedKey = resolver.strategy().normalize(originalKey);
    String normalizedPrefix = resolver.strategy().normalize(rawPrefix);

    String[] prefixParts = normalizedPrefix.split("\\.");
    String[] keyParts = normalizedKey.split("\\.");

    // Step 2: prefix match (logical segments)
    if (keyParts.length <= prefixParts.length) {
      return Optional.empty();
    }

    for (int i = 0; i < prefixParts.length; i++) {
      if (!keyParts[i].equals(prefixParts[i])) {
        return Optional.empty();
      }
    }

    // Step 3: extract from ORIGINAL key by segment count
    return Optional.of(extractFromOriginal(originalKey, prefixParts.length));
  }

  private String extractFromOriginal(String originalKey, int prefixSegments) {
    int segmentCount = 0;

    for (int i = 0; i < originalKey.length(); i++) {
      char c = originalKey.charAt(i);

      if (c == '.' || c == '-' || c == '_') {
        segmentCount++;
        if (segmentCount == prefixSegments) {
          return originalKey.substring(i + 1);
        }
      }
    }

    return originalKey;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(String prefix, Class<T> type) {
    if (!cachedConfiguration.containsKey(type)) {
      T config = PropertyBinder.bind(this).get(prefix, type);
      cachedConfiguration.put(type, config);
    }
    return (T) cachedConfiguration.get(type);
  }

  @Override
  public <T> T get(Class<T> type) {
    LoadProperties annotation = type.getAnnotation(LoadProperties.class);
    if (ObjectUtils.isNotEmpty(annotation)) {
      String prefix = annotation.prefix();
      if (StringUtils.isNotBlank(prefix)) {
        return get(prefix, type);
      }
    }
    throw new InvalidConfigurationPropertiesException(String.format(
        "Type %s is not a configuration properties, or unable to find the matching prefix",
        type.getName()));
  }

  @Override
  public Optional<String> get(String key) {
    return resolver.get(key);
  }

  @Override
  public String get(String key, String fallback) {
    return resolver.get(key, fallback);
  }

  // ------------------------------------------------------------
  // Simple access
  // ------------------------------------------------------------

  @Override
  public boolean contains(String key) {
    return resolver.contains(key);
  }

  @Override
  public PropertyNameStrategy strategy() {
    return resolver.strategy();
  }

  // ------------------------------------------------------------
  // Prefix access (NEW)
  // ------------------------------------------------------------

  @Override
  public Map<String, PropertyValue> properties() {
    return resolver.properties();
  }

  @Override
  public void reload() {
    resolver.reload();
    cachedConfiguration.forEach((type, value) -> {
      LoadProperties annotation = type.getAnnotation(LoadProperties.class);
      if (ObjectUtils.isNotEmpty(annotation)) {
        String prefix = annotation.prefix();
        if (StringUtils.isNotBlank(prefix)) {
          Object config = PropertyBinder.bind(this).get(prefix, type);
          cachedConfiguration.put(type, config);
        }
      }
    });
  }
}
