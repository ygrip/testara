package io.github.ygrip.testara.core.config;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface PropertyResolver {

  Optional<String> get(String key);

  String get(String key, String fallback);

  boolean contains(String key);

  PropertyNameStrategy strategy();

  Map<String, PropertyValue> properties();

  void reload();

  default Map<String, String> sourceProperties(){
    Map<String, String> props = new HashMap<>();
    sources().forEach(source -> {
      props.putAll(source.load(props));
    });
    return props;
  }

  default Stream<PropertySource> sources() {
    ServiceLoader<PropertySource> loader =
        ServiceLoader.load(PropertySource.class, Thread.currentThread().getContextClassLoader());

    return StreamSupport.stream(loader.spliterator(), false)
        .collect(Collectors.toMap(PropertySource::getClass, ps -> ps, (a, b) -> a))
        .values()
        .stream()
        .sorted(Comparator.comparingInt(PropertySource::priority));
  }

  default PropertyValue toValue(String key, Object value) {
    return PropertyValue.of(key, value, strategy());
  }

  class PropertyValue {
    private final String key;
    private final String normalizedKey;
    private Object value;

    private PropertyValue(String key, Object value, PropertyNameStrategy strategy) {
      this.key = key;
      this.normalizedKey = strategy.normalize(key);
      this.value = value;
    }

    static PropertyValue of(String key, Object value, PropertyNameStrategy strategy) {
      return new PropertyValue(key, value, strategy);
    }

    public void withValue(String value) {
      this.value = value;
    }

    public String key() {
      return key;
    }

    public Object value() {
      return value;
    }

    public String normalizedKey() {
      return normalizedKey;
    }
  }
}
