package io.github.ygrip.testara.command.parser;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;
import static io.github.ygrip.testara.command.CommandExecutor.isCacheableCommand;
import static io.github.ygrip.testara.command.CommandExecutor.parseRegisteredCommand;
import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static io.github.ygrip.testara.core.support.CommonHelper.parseStringToObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.support.CommonHelper;

public final class CommandPatternObjectConverter implements ObjectConverter {
  private static final int DEFAULT_CACHE_SIZE = 500;
  private final OptimizedLRUCache<Object, Object> CACHEABLE_CONTENTS;

  public CommandPatternObjectConverter() {
    this.CACHEABLE_CONTENTS = new OptimizedLRUCache<>(DEFAULT_CACHE_SIZE);
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
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
    if (input instanceof CommandModel) {
      result = processCacheableCommand((CommandModel) input);
    } else if (input instanceof String) {
      result = processCacheableString((String) input);
    } else {
      result = input;
    }

    return (T) result;
  }

  /**
   * Process cacheable command model with improved error handling.
   */
  private Object processCacheableCommand(CommandModel command) {
    boolean cached = isCacheableCommand(command);
    Object temp = null;
    try {
      temp = executeCommand(command);
    } catch (Exception e) {
      cached = true;
    }
    if (cached) {
      CACHEABLE_CONTENTS.put(command, temp);
    }
    return temp;
  }

  /**
   * Process cacheable string with improved error handling.
   */
  private Object processCacheableString(String input) {
    boolean cached;
    Object temp;
    try {
      CommandModel command = parseRegisteredCommand(input);
      cached = isCacheableCommand(command);
      temp = executeCommand(command);
    } catch (Exception e) {
      try {
        temp = parseStringToObject(input);
      } catch (Exception parseException) {
        temp = input; // Return original string if parsing fails
      }
      cached = true;
    }
    if (cached) {
      CACHEABLE_CONTENTS.put(input, temp);
    }
    return temp;
  }

  @Override
  public <T> T convert(Object input) {
    return parseNestedValue(input);
  }
}
