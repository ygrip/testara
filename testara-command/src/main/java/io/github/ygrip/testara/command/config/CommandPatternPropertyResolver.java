package io.github.ygrip.testara.command.config;

import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.core.config.PropertyNameStrategy;
import io.github.ygrip.testara.core.config.PropertyNameStrategyLoader;
import io.github.ygrip.testara.core.config.PropertyResolver;
import io.github.ygrip.testara.core.model.PlaceholderLookup;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandPatternPropertyResolver implements PropertyResolver {

  private final Map<String, PropertyValue> properties;
  private final PropertyNameStrategy nameStrategy;
  private final PlaceholderLookup lookup;

  public CommandPatternPropertyResolver() {
    this.nameStrategy = PropertyNameStrategyLoader.load();
    this.properties = new ConcurrentHashMap<>();
    this.lookup = key -> {
      String result = null;
      try {
        CommandModel commandPattern = CommandExecutor.parseCommand(key);
        if (ObjectUtils.isNotEmpty(commandPattern)) {
          return commandPattern;
        }
      } catch (Exception ignored) {

      }
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
    return Optional.ofNullable(properties.get(nameStrategy.normalize(key))).map(this::resolve).map(Object::toString);
  }

  private Object resolve(PropertyValue propertyValue) {
    if (ObjectUtils.isEmpty(propertyValue)) {
      return null;
    }
    Object value = propertyValue.value();
    if (ObjectUtils.isEmpty(value)) {
      return value;
    }
    if (value instanceof CommandModel commandModel) {
      return CommandExecutor.executeCommand(commandModel);
    } else {
      return value.toString();
    }
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
      Object resolved = CommandPatternPlaceholderResolver.resolve(v, lookup);
      normalized.put(nameStrategy.normalize(k), toValue(k, resolved));
    });
    return normalized;
  }

  @Override
  public void reload() {
    this.properties.putAll(load());
  }
}

