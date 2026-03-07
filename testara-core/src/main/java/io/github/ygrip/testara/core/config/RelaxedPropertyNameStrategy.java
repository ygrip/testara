package io.github.ygrip.testara.core.config;

public final class RelaxedPropertyNameStrategy implements PropertyNameStrategy {

  @Override
  public String normalize(String name) {
    StringBuilder result = new StringBuilder();
    for (char c : name.toCharArray()) {
      if (c == '-' || c == '_') {
        result.append('.');
      } else if (Character.isUpperCase(c)) {
        result.append('.').append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }

    return result.toString().replaceAll("\\.+", ".").toLowerCase();
  }
}

