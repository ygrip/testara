package io.github.ygrip.testara.core.support;

import org.apache.commons.lang3.StringUtils;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

public final class JsonPathUtil {

  private JsonPathUtil() {
    // utility class
  }

  public static String toValidJsonPath(String input) {
    if (StringUtils.isBlank(input)) {
      return input;
    }

    StringBuilder result = new StringBuilder(input.length() + 10);
    result.append('$');

    int i = 0;
    int len = input.length();

    while (i < len) {

      // skip spaces
      while (i < len && input.charAt(i) == ' ')
        i++;
      if (i >= len)
        break;

      int start = i;

      while (i < len && input.charAt(i) != ' ')
        i++;

      String token = input.substring(start, i);

      if (isNumeric(token)) {
        result.append('[')
          .append(token)
          .append(']');
      } else if (token.startsWith("?")) {
        result.append("[?(")
          .append(token.substring(1))
          .append(")]");
      } else {
        appendProperty(result, token);
      }
    }

    return result.toString();
  }

  private static void appendProperty(StringBuilder sb, String prop) {

    if (isSimpleIdentifier(prop)) {
      sb.append('.')
        .append(prop);
    } else {
      sb.append("['")
        .append(prop)
        .append("']");
    }
  }

  private static boolean isNumeric(String s) {
    int n = s.length();
    if (n == 0)
      return false;
    for (int i = 0; i < n; i++) {
      if (!Character.isDigit(s.charAt(i)))
        return false;
    }
    return true;
  }

  // valid Java identifier style = safe for dot notation
  private static boolean isSimpleIdentifier(String s) {
    if (s.isEmpty())
      return false;
    if (!Character.isLetter(s.charAt(0)) && s.charAt(0) != '_')
      return false;

    for (int i = 1; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '_')
        return false;
    }
    return true;
  }

  public static boolean isValidJsonPath(String path) {
    try {
      JsonPath.compile(path);
      return true;
    } catch (InvalidPathException e) {
      return false;
    }
  }
}
