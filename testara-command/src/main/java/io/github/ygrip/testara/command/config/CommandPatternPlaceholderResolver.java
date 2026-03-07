package io.github.ygrip.testara.command.config;

import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.core.model.PlaceholderLookup;
import org.apache.commons.lang3.ObjectUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class CommandPatternPlaceholderResolver {

  private static final String PREFIX = "${";
  private static final char SUFFIX = '}';
  private static final char DEFAULT_SEPARATOR = ':';
  private static final int MAX_DEPTH = 32;

  public static Object resolve(String value, PlaceholderLookup lookup) {
    if (value == null) {
      return value;
    }
    if (!value.contains(PREFIX)) {
      try {
        CommandModel commandPattern = CommandExecutor.parseCommand(value);
        if (ObjectUtils.isNotEmpty(commandPattern)) {
          return commandPattern;
        }
      } catch (Exception ignored) {

      }
    }
    return resolveInternal(value, lookup, new HashSet<>(), 0);
  }

  private static Object resolveInternal(Object value, PlaceholderLookup lookup, Set<String> visiting, int depth) {
    if (depth > MAX_DEPTH) {
      throw new IllegalStateException("Maximum placeholder depth exceeded");
    }

    CommandModel commandModelOut = CommandModel.builder().command("combine").build();
    if (value == null) {
      return null;
    }

    if (value instanceof CommandModel) {
      return value;
    }

    boolean hasCommand = false;
    String valueString = value.toString();
    StringBuilder outString = new StringBuilder(valueString.length());
    int i = 0;

    while (i < valueString.length()) {
      if (!valueString.startsWith(PREFIX, i)) {
        outString.append(valueString.charAt(i++));
        continue;
      }

      int start = i + PREFIX.length();
      int end = findPlaceholderEnd(valueString, start);

      if (end < 0) {
        throw new IllegalArgumentException("Unclosed placeholder: " + value);
      }

      String placeholder = valueString.substring(start, end);

      String key;
      String fallback = null;

      int sep = findDefaultSeparator(placeholder);
      if (sep >= 0) {
        key = placeholder.substring(0, sep);
        fallback = placeholder.substring(sep + 1);
      } else {
        key = placeholder;
      }

      if (!visiting.add(key)) {
        throw new IllegalStateException("Circular placeholder reference: " + key);
      }

      Object resolved = Optional.ofNullable(lookup.lookup(key)).map(Object::toString).orElse(null);
      if (resolved != null) {
        resolved = resolveInternal(resolved, lookup, visiting, depth + 1);
      } else if (fallback != null) {
        resolved = resolveInternal(fallback, lookup, visiting, depth + 1);
      }

      visiting.remove(key);

      if (resolved != null) {
        if (resolved instanceof CommandModel commandModel) {
          hasCommand = true;
          commandModel.addParameter(commandModel);
        } else {
          outString.append(resolved);
          if (hasCommand) {
            commandModelOut.addParameter(outString.toString());
            outString.setLength(0);
          }
        }
      }

      i = end + 1;
    }

    if (hasCommand) {
      return commandModelOut;
    } else {
      return outString.toString();
    }
  }

  private static int findPlaceholderEnd(String value, int start) {
    int nested = 0;

    for (int i = start; i < value.length(); i++) {
      char c = value.charAt(i);
      if (value.startsWith(PREFIX, i)) {
        nested++;
        i++;
      } else if (c == SUFFIX) {
        if (nested-- == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static int findDefaultSeparator(String placeholder) {
    int nested = 0;
    for (int i = 0; i < placeholder.length(); i++) {
      char c = placeholder.charAt(i);
      if (placeholder.startsWith(PREFIX, i)) {
        nested++;
        i++;
      } else if (c == SUFFIX) {
        nested--;
      } else if (c == DEFAULT_SEPARATOR && nested == 0) {
        return i;
      }
    }
    return -1;
  }
}