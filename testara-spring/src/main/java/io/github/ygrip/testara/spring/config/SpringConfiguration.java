package io.github.ygrip.testara.spring.config;

import io.github.ygrip.testara.core.config.*;
import io.github.ygrip.testara.core.error.InvalidConfigurationPropertiesException;
import io.github.ygrip.testara.spring.context.SpringContextHolder;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring-backed TestConfiguration implementation.
 * <p>
 * This configuration bridges testara-core's TestConfiguration with Spring's Environment:
 * - Properties from Spring Environment are available via testara's API
 * - Properties from testara's PropertySource are also included
 * - Supports @LoadProperties annotation for typed configuration binding
 * <p>
 * Property resolution order:
 * 1. Spring Environment (highest priority when available)
 * 2. testara-core PropertySources
 */
@Log4j2
public final class SpringConfiguration implements TestConfiguration {

  private final PropertyResolver testaraResolver;
  private final PropertyNameStrategy nameStrategy;
  private final ConcurrentHashMap<Class<?>, Object> cachedConfiguration;
  private volatile Map<String, PropertyValue> mergedProperties;

  public SpringConfiguration() {
    this.testaraResolver = PropertyResolverLoader.load();
    this.nameStrategy = PropertyNameStrategyLoader.load();
    this.cachedConfiguration = new ConcurrentHashMap<>();
    this.mergedProperties = null; // Lazy initialization
  }

  // ------------------------------------------------------------
  // Property Access
  // ------------------------------------------------------------

  @Override
  public Optional<String> get(String key) {
    // Try Spring Environment first
    Environment env = getSpringEnvironment();
    if (env != null) {
      String value = env.getProperty(key);
      if (value != null) {
        return Optional.of(value);
      }
      // Try normalized key
      String normalizedKey = nameStrategy.normalize(key);
      value = env.getProperty(normalizedKey);
      if (value != null) {
        return Optional.of(value);
      }
    }

    // Fall back to testara resolver
    return testaraResolver.get(key);
  }

  @Override
  public String get(String key, String fallback) {
    return get(key).orElse(fallback);
  }

  @Override
  public boolean contains(String key) {
    Environment env = getSpringEnvironment();
    if (env != null && env.containsProperty(key)) {
      return true;
    }
    return testaraResolver.contains(key);
  }

  @Override
  public PropertyNameStrategy strategy() {
    return nameStrategy;
  }

  // ------------------------------------------------------------
  // Prefix-based Access
  // ------------------------------------------------------------

  @Override
  public Map<String, PropertyValue> getByPrefix(String prefix) {
    String normalizedPrefix = nameStrategy.normalize(prefix);
    Map<String, PropertyValue> result = new LinkedHashMap<>();

    properties().forEach((key, value) -> {
      if (!key.startsWith(normalizedPrefix + ".")) {
        return;
      }

      String normalizedKey = key.substring(normalizedPrefix.length() + 1);
      Optional<String> originalKey = extractKey(value.key(), prefix);
      result.put(originalKey.orElse(normalizedKey), value);
    });

    return result;
  }

  @Override
  public Map<String, PropertyValue> properties() {
    if (mergedProperties == null) {
      synchronized (this) {
        if (mergedProperties == null) {
          mergedProperties = loadMergedProperties();
        }
      }
    }
    return mergedProperties;
  }

  private Map<String, PropertyValue> loadMergedProperties() {
    Map<String, PropertyValue> result = new ConcurrentHashMap<>();

    // Start with testara properties
    result.putAll(testaraResolver.properties());

    // Overlay with Spring properties (higher priority)
    Environment env = getSpringEnvironment();
    if (env instanceof ConfigurableEnvironment configEnv) {
      MutablePropertySources sources = configEnv.getPropertySources();
      sources.forEach(source -> {
        if (source instanceof EnumerablePropertySource<?> enumerable) {
          for (String name : enumerable.getPropertyNames()) {
            Object value = enumerable.getProperty(name);
            if (value != null) {
              String normalizedKey = nameStrategy.normalize(name);
              result.put(normalizedKey, toValue(name, value));
            }
          }
        }
      });
    }

    log.debug("Loaded {} merged properties from Spring Environment and testara sources", result.size());
    return result;
  }

  // ------------------------------------------------------------
  // Typed Configuration Binding
  // ------------------------------------------------------------

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

  // ------------------------------------------------------------
  // Reload Support
  // ------------------------------------------------------------

  @Override
  public void reload() {
    testaraResolver.reload();
    
    // Clear caches to pick up new values
    synchronized (this) {
      mergedProperties = null;
    }
    
    // Reload cached configurations
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
    
    log.trace("SpringConfiguration reloaded");
  }

  // ------------------------------------------------------------
  // Internal Helpers
  // ------------------------------------------------------------

  private Environment getSpringEnvironment() {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();
    return ctx != null ? ctx.getEnvironment() : null;
  }

  private Optional<String> extractKey(String originalKey, String rawPrefix) {
    if (originalKey == null || rawPrefix == null) {
      return Optional.empty();
    }

    String normalizedKey = nameStrategy.normalize(originalKey);
    String normalizedPrefix = nameStrategy.normalize(rawPrefix);

    String[] prefixParts = normalizedPrefix.split("\\.");
    String[] keyParts = normalizedKey.split("\\.");

    if (keyParts.length <= prefixParts.length) {
      return Optional.empty();
    }

    for (int i = 0; i < prefixParts.length; i++) {
      if (!keyParts[i].equals(prefixParts[i])) {
        return Optional.empty();
      }
    }

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
}
