package io.github.ygrip.testara.core.model;

@FunctionalInterface
public interface PlaceholderLookup {
  Object lookup(String key);
}

