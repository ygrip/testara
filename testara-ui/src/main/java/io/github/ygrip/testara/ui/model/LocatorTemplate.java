package io.github.ygrip.testara.ui.model;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocatorTemplate {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_-]*)}");

  private LocatorTemplate() {

  }

  public static boolean hasTemplate(String value) {
    return value != null && PLACEHOLDER.matcher(value)
      .find();
  }

  public static Set<String> parameterNames(String value) {
    Set<String> names = new LinkedHashSet<>();
    if (value == null) {
      return names;
    }
    Matcher matcher = PLACEHOLDER.matcher(value);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  public static String render(String template, Selector selector, Map<String, ?> params) {
    if (template == null) {
      return null;
    }

    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String name = matcher.group(1);
      if (!params.containsKey(name)) {
        throw new IllegalArgumentException("Missing locator parameter: " + name);
      }

      String replacement = LocatorValueEscaper.escape(selector, params.get(name));
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }

    matcher.appendTail(result);
    return result.toString();
  }
}
