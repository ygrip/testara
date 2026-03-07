package io.github.ygrip.testara.core.converter;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static io.github.ygrip.testara.core.support.CommonHelper.parseStringToObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

import io.github.ygrip.testara.core.support.CommonHelper;

public final class NestedObjectConverter implements ObjectConverter {
  private static final int DEFAULT_CACHE_SIZE = 500;
  private final OptimizedLRUCache<Object, Object> CACHEABLE_CONTENTS;

  public NestedObjectConverter() {
    this.CACHEABLE_CONTENTS = new OptimizedLRUCache<>(DEFAULT_CACHE_SIZE);
  }

  @SuppressWarnings("unchecked")
  private <T> T parseNestedValue(Object input) {
    if (isBlank(input)) {
      return (T) input;
    } else if (CommonHelper.isCollection(input)) {
      return (T) ((Collection<?>) input).stream()
        .map(this::parseNestedValue)
        .collect(Collectors.toList());
    } else if (input instanceof HashMap) {
      ((HashMap<?, Object>) input).replaceAll((key, value) -> parseNestedValue(value));
      return (T) input;
    } else {
      return convertFromCache(input);
    }
  }

  /**
   * Optimized cacheable content retrieval with LRU eviction
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T convertFromCache(Object input) {
    if (input == null) {
      return null;
    }

    // Check cache first
    Object cached = CACHEABLE_CONTENTS.get(input);
    if (cached != null) {
      return (T) cached;
    }

    Object result;
    if (input instanceof String) {
      result = processCacheableString((String) input);
    } else {
      result = input;
    }

    return (T) result;
  }

  /**
   * Process cacheable string with improved error handling.
   */
  private Object processCacheableString(String input) {
    Object temp;
    try {
      temp = parseStringToObject(input);
    } catch (Exception e) {
      temp = input;
    }
    CACHEABLE_CONTENTS.put(input, temp);
    return temp;
  }

  @Override
  public <T> T convert(Object input) {
    return parseNestedValue(input);
  }
}
