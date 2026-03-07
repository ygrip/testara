package io.github.ygrip.testara.core.config;

import io.github.ygrip.testara.core.model.PlaceholderLookup;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class PlaceholderResolver {

  private static final String PREFIX = "${";
  private static final char SUFFIX = '}';
  private static final char DEFAULT_SEPARATOR = ':';
  private static final int MAX_DEPTH = 32;

  public static String resolve(String value, PlaceholderLookup lookup) {
    if (value == null || !value.contains(PREFIX)) {
      return value;
    }
    return resolveInternal(value, lookup, new HashSet<>(), 0);
  }

  private static String resolveInternal(String value, PlaceholderLookup lookup, Set<String> visiting, int depth) {
    if (depth > MAX_DEPTH) {
      throw new IllegalStateException("Maximum placeholder depth exceeded");
    }

    StringBuilder out = new StringBuilder(value.length());
    int i = 0;

    while (i < value.length()) {
      if (!value.startsWith(PREFIX, i)) {
        out.append(value.charAt(i++));
        continue;
      }

      int start = i + PREFIX.length();
      int end = findPlaceholderEnd(value, start);

      if (end < 0) {
        throw new IllegalArgumentException("Unclosed placeholder: " + value);
      }

      String placeholder = value.substring(start, end);

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

      String resolved = Optional.ofNullable(lookup.lookup(key)).map(Object::toString).orElse(null);
      if (resolved != null) {
        resolved = resolveInternal(resolved, lookup, visiting, depth + 1);
      } else if (fallback != null) {
        resolved = resolveInternal(fallback, lookup, visiting, depth + 1);
      }

      visiting.remove(key);

      if (resolved != null) {
        out.append(resolved);
      }

      i = end + 1;
    }

    return out.toString();
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