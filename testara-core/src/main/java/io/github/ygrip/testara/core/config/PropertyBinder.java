package io.github.ygrip.testara.core.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.core.support.ScalarConverter;

public final class PropertyBinder {

  private PropertyBinder() {
  }

  public static Binder bind(TestConfiguration configuration) {
    return new Binder(configuration);
  }

  public static class Binder {
    private final static ScalarConverter converter = new ScalarConverter();
    private final TestConfiguration resolver;

    Binder(TestConfiguration resolver) {
      this.resolver = resolver;
    }

    /* =========================
       Public API
       ========================= */

    public <T> T get(String prefix, Class<T> type) {
      Object result = bindInternal(prefix, type, type);
      return type.cast(result);
    }

    private Object safeConvert(String value, Class<?> rawType) {
      try {
        return converter.convert(value, rawType);
      } catch (Exception ignored) {
        return null;
      }
    }

    /* =========================
       Core Dispatcher
       ========================= */

    private Object bindInternal(String prefix, Class<?> rawType, Type genericType) {

      if (isScalar(rawType)) {
        return resolver.get(prefix)
          .map(v -> safeConvert(v, rawType))
          .orElse(null);
      }

      if (List.class.isAssignableFrom(rawType)) {
        Class<?> elementType = extractCollectionElementType(genericType);
        return bindList(prefix, elementType);
      }

      if (Set.class.isAssignableFrom(rawType)) {
        Class<?> elementType = extractCollectionElementType(genericType);
        return bindSet(prefix, elementType);
      }

      if (Map.class.isAssignableFrom(rawType)) {
        return bindMap(prefix, genericType);
      }

      if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
        return null;
      }

      return bindObject(prefix, rawType);
    }

    private Set<?> bindSet(String prefix, Class<?> elementType) {

      List<?> list = bindList(prefix, elementType);

      if (list.isEmpty()) {
        return Set.of();
      }

      return new LinkedHashSet<>(list);
    }

    private Map<?, ?> bindMap(String prefix, Type genericType) {

      Map<String, PropertyResolver.PropertyValue> values = resolver.getByPrefix(prefix);

      if (values.isEmpty()) {
        return Map.of();
      }

      Class<?> keyClass = extractMapKeyType(genericType);
      Class<?> valueClass = extractMapValueType(genericType);
      Type valueType = extractMapValueTypeFull(genericType);

      Map<Object, Object> result = new HashMap<>();
      Set<String> keysToBindAsNested = new HashSet<>();

      // collect keys that contain dot (.) — nested objects, e.g. key1.foo
      values.forEach((key, value) -> {
        if (key.contains(".")) {
          String firstKey = key.split("\\.", 2)[0];
          keysToBindAsNested.add(firstKey);
        }
      });

      // when value type is List or Set, also collect base keys from "baseKey[index]" entries
      if (List.class.isAssignableFrom(valueClass) || Set.class.isAssignableFrom(valueClass)) {
        values.forEach((key, value) -> {
          String listBaseKey = extractListKeyBase(key);
          if (listBaseKey != null) {
            keysToBindAsNested.add(listBaseKey);
          }
        });
      }

      // bind nested / list / set values first
      for (String segment : keysToBindAsNested) {
        Object mapKey = convertMapKey(segment, keyClass);
        if (mapKey == null) {
          continue;
        }
        String fullKey = prefix + "." + resolver.strategy().normalize(segment);
        Object bound = bindInternal(fullKey, valueClass, valueType);
        result.put(mapKey, bound);
      }

      // bind scalar / flat entries (skip keys that are list indices, e.g. key1[0])
      values.forEach((key, value) -> {
        if (key.contains(".")) {
          return; // already handled as nested
        }
        if (extractListKeyBase(key) != null) {
          return; // key like key1[0] — already handled as list under key1
        }
        Object mapKey = convertMapKey(key, keyClass);
        if (mapKey == null) {
          return;
        }
        String fullKey = prefix + "." + resolver.strategy().normalize(key);
        Object bound = bindInternal(fullKey, valueClass, valueType);
        result.put(mapKey, bound);
      });

      return result;
    }

    /**
     * If key is a list-index form like "baseKey[0]", "baseKey.[0]", or "baseKey[12]", returns "baseKey".
     * Trailing dots before the bracket are stripped so "chrome.[0]" yields "chrome" (avoids duplicate "chrome"/"chrome.").
     */
    private String extractListKeyBase(String key) {
      if (key == null) {
        return null;
      }
      int bracket = key.indexOf('[');
      if (bracket <= 0) {
        return null;
      }
      String tail = key.substring(bracket);
      if (tail.matches("\\[\\d+\\]")) {
        String base = key.substring(0, bracket);
        // strip trailing dots so "chrome.[0]" -> "chrome", not "chrome."
        int end = base.length();
        while (end > 0 && base.charAt(end - 1) == '.') {
          end--;
        }
        return end == 0 ? null : base.substring(0, end);
      }
      return null;
    }

    /**
     * Converts a property key (string) to the map's key type (e.g. enum, Integer).
     * Returns null if conversion fails or key type is not supported.
     */
    private Object convertMapKey(String key, Class<?> keyClass) {
      if (keyClass == null || keyClass == Object.class || keyClass == String.class) {
        return key;
      }
      return safeConvert(key.trim(), keyClass);
    }

    private Class<?> extractMapKeyType(Type genericType) {
      if (genericType instanceof ParameterizedType p) {
        Type keyType = p.getActualTypeArguments()[0];
        if (keyType instanceof Class<?> c) {
          return c;
        }
        if (keyType instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
          return c;
        }
      }
      return String.class;
    }

    private Class<?> extractMapValueType(Type genericType) {

      if (genericType instanceof ParameterizedType p) {
        Type valueType = p.getActualTypeArguments()[1];

        if (valueType instanceof Class<?> c) {
          return c;
        }

        if (valueType instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
          return c;
        }
      }

      return Object.class;
    }

    /**
     * Returns the full value type (including generics) for nested type binding.
     */
    private Type extractMapValueTypeFull(Type genericType) {
      if (genericType instanceof ParameterizedType p) {
        return p.getActualTypeArguments()[1];
      }
      return null;
    }


    /* =========================
       Object Binding
       ========================= */

    private Object bindObject(String prefix, Class<?> type) {

      if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
        return null;
      }

      Map<String, Object> values = resolver.getByPrefix(prefix)
        .entrySet()
        .stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          e -> e.getValue()
            .value()
        ));

      Optional<String> optional = resolver.get(prefix);

      if (ObjectUtils.isEmpty(values) && optional.isPresent()) {
        return optional.get();
      }

      if (type.isRecord()) {
        return bindRecord(prefix, type, values);
      }

      Constructor<?> noArg = findPublicNoArgConstructor(type);
      Object instance;

      if (noArg != null) {
        instance = instantiate(noArg);
      } else {
        Constructor<?> ctor = findUsableConstructor(type);
        instance = bindByConstructor(prefix, type, ctor, values);
      }

      populateFieldsLenient(prefix, instance, values);
      return instance;
    }

    /* =========================
       Constructor Binding (Lenient)
       ========================= */

    private Object bindByConstructor(String prefix, Class<?> type, Constructor<?> ctor, Map<String, Object> values) {
      Parameter[] params = ctor.getParameters();
      List<String> paramNames = resolveParameters(type, ctor);

      Object[] args = new Object[params.length];

      Set<String> normalizedKeys = values.keySet()
        .stream()
        .map(key -> resolver.strategy()
          .normalize(key))
        .collect(Collectors.toSet());

      for (int i = 0; i < params.length; i++) {
        String name = resolver.strategy()
          .normalize(paramNames.get(i));
        String key = prefix + "." + name;

        Object value = null;

        Class<?> paramType = params[i].getType();
        Map<String, Object> data = resolver.getByPrefix(key)
          .entrySet()
          .stream()
          .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> e.getValue()
              .value()
          ));
        if (normalizedKeys.contains(name) || ObjectUtils.isNotEmpty(data)) {
          value = bindInternal(key, paramType, params[i].getParameterizedType());
        }

        args[i] = defaultIfPrimitive(value, params[i].getType());
      }

      try {
        return ctor.newInstance(args);
      } catch (Exception e) {
        throw new RuntimeException("Failed to instantiate " + type.getName(), e);
      }
    }

    private Object bindRecord(String prefix, Class<?> type, Map<String, Object> values) {
      RecordComponent[] components = type.getRecordComponents();
      Constructor<?> ctor = type.getDeclaredConstructors()[0];

      Object[] args = new Object[components.length];

      Set<String> normalizedKeys = values.keySet()
        .stream()
        .map(key -> resolver.strategy()
          .normalize(key))
        .collect(Collectors.toSet());

      for (int i = 0; i < components.length; i++) {
        String name = resolver.strategy()
          .normalize(components[i].getName());
        String key = prefix + "." + name;

        Object value = null;

        if (normalizedKeys.contains(name)) {
          value = bindInternal(key, components[i].getType(), components[i].getGenericType());
        }

        args[i] = defaultIfPrimitive(value, components[i].getType());
      }

      try {
        return ctor.newInstance(args);
      } catch (Exception e) {
        throw new RuntimeException("Failed to instantiate record " + type.getName(), e);
      }
    }

    /* =========================
       Field Population (Defaults Preserved)
       ========================= */

    /**
     * Collects all declared fields from the given class and its superclasses (up to but not including Object).
     * Superclass fields are listed first so that subclass fields take precedence when binding the same property name.
     */
    private List<Field> getAllFieldsIncludingSuper(Class<?> type) {
      List<Field> fields = new ArrayList<>();
      Class<?> current = type;
      while (current != null && current != Object.class) {
        for (Field f : current.getDeclaredFields()) {
          fields.add(f);
        }
        current = current.getSuperclass();
      }
      return fields;
    }

    private void populateFieldsLenient(String prefix, Object instance, Map<String, Object> values) {
      Class<?> type = instance.getClass();

      Set<String> normalizedKeys = values.keySet()
        .stream()
        .map(key -> resolver.strategy()
          .normalize(key))
        .collect(Collectors.toSet());

      for (Field field : getAllFieldsIncludingSuper(type)) {

        String name = resolver.strategy()
          .normalize(field.getName());
        String key = prefix + "." + name;

        if (!normalizedKeys.contains(name)) {
          Map<String, PropertyResolver.PropertyValue> nestedValues = resolver.getByPrefix(key);
          if (ObjectUtils.isEmpty(nestedValues)) {
            continue; // preserve default
          }
        }

        Object value = bindInternal(key, field.getType(), field.getGenericType());

        if (value == null && field.getType()
          .isPrimitive()) {
          continue;
        }

        try {
          field.setAccessible(true);
          field.set(instance, value);
        } catch (Exception e) {
          // swallow – lenient mode
        }
      }
    }

    /* =========================
       List Binding
       ========================= */

    private List<?> bindList(String prefix, Class<?> elementType) {

      // indexed binding: support both "0"/"1" (dot) and "[0]"/"[1]" (bracket) list keys
      Map<Integer, Map.Entry<String, PropertyResolver.PropertyValue>> indexed = new TreeMap<>();

      resolver.getByPrefix(prefix)
        .forEach((k, v) -> {
          Integer idx = parseIndex(k);
          if (idx != null) {
            indexed.put(idx, Map.entry(k, v));
          }
        });

      if (!indexed.isEmpty()) {
        return indexed.entrySet()
          .stream()
          .sorted(Map.Entry.comparingByKey())
          .map(e -> {
            String segmentKey = e.getValue().getKey();
            String fullKey = segmentKey.startsWith("[")
              ? prefix + segmentKey
              : prefix + "." + segmentKey;
            Object raw = e.getValue().getValue().value();
            if (isScalar(elementType) && raw != null) {
              return safeConvert(raw.toString(), elementType);
            }
            return bindInternal(fullKey, elementType, null);
          })
          .toList();
      }

      // comma-separated fallback
      return resolver.get(prefix)
        .map(v -> Arrays.stream(v.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(s -> elementType == Object.class ? s : safeConvert(s, elementType))
          .toList())
        .orElse(List.of());
    }

    /* =========================
       Constructor Resolution
       ========================= */

    private Constructor<?> findPublicNoArgConstructor(Class<?> type) {
      for (Constructor<?> c : type.getConstructors()) {
        if (c.getParameterCount() == 0) {
          return c;
        }
      }
      return null;
    }

    private Constructor<?> findUsableConstructor(Class<?> type) {
      return Arrays.stream(type.getDeclaredConstructors())
        .max(Comparator.comparingInt(Constructor::getParameterCount))
        .map(c -> {
          c.setAccessible(true);
          return c;
        })
        .orElseThrow(() -> new IllegalStateException("No constructor found for " + type.getName()));
    }


    private List<String> resolveParameters(Class<?> type, Constructor<?> ctor) {
      if (type.isRecord()) {
        return Arrays.stream(type.getRecordComponents())
          .map(RecordComponent::getName)
          .toList();
      }

      return Arrays.stream(ctor.getParameters())
        .map(p -> p.getName())
        .toList();
    }

    /* =========================
       Utilities
       ========================= */

    private boolean isScalar(Class<?> type) {
      return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class
        || type.isEnum();
    }

    private Object defaultIfPrimitive(Object value, Class<?> type) {
      if (value != null)
        return value;
      if (!type.isPrimitive())
        return null;
      if (type == boolean.class)
        return false;
      if (type == int.class)
        return 0;
      if (type == long.class)
        return 0L;
      if (type == double.class)
        return 0d;
      if (type == float.class)
        return 0f;
      if (type == short.class)
        return (short) 0;
      if (type == byte.class)
        return (byte) 0;
      if (type == char.class)
        return '\0';
      return null;
    }

    private Class<?> extractCollectionElementType(Type genericType) {

      if (genericType instanceof ParameterizedType p) {
        Type arg = p.getActualTypeArguments()[0];

        if (arg instanceof Class<?> c) {
          return c;
        }

        if (arg instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
          return c;
        }
      }

      // fallback: Spring-style behavior
      return Object.class;
    }

    private Integer parseIndex(String key) {
      if (key.matches("\\[\\d+]")) {
        return Integer.parseInt(key.substring(1, key.length() - 1));
      }
      if (key.matches("\\d+")) {
        return Integer.parseInt(key);
      }
      return null;
    }

    private Object instantiate(Constructor<?> ctor) {
      try {
        return ctor.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(
          "Failed to instantiate " + ctor.getDeclaringClass()
            .getName(), e
        );
      }
    }
  }
}