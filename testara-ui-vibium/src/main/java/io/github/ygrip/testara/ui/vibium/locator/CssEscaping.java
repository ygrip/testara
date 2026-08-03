package io.github.ygrip.testara.ui.vibium.locator;

/**
 * Minimal, pragmatic CSS escaping for user-supplied id/class/name values that get concatenated
 * into a raw CSS selector string handed to Vibium. Not a full CSS.escape spec implementation —
 * just enough that quotes, backslashes, whitespace and other CSS-special characters in a value
 * cannot break out of, or corrupt the meaning of, the generated selector.
 */
final class CssEscaping {

  private static final String CSS_SPECIAL_CHARS = "!\"#$%&'()*+,./:;<=>?@[\\]^`{|}~ ";

  private CssEscaping() {
  }

  /**
   * Escape a value used as a bare CSS identifier fragment (an id or class name, without the
   * leading {@code #} or {@code .}). A leading digit is invalid in a bare CSS identifier and is
   * escaped as a codepoint; any other CSS-special character (including whitespace, which would
   * otherwise be read as a descendant combinator) is backslash-escaped.
   */
  static String escapeIdentifier(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    StringBuilder result = new StringBuilder(value.length() + 4);
    int start = 0;
    char first = value.charAt(0);
    if (Character.isDigit(first)) {
      result.append('\\').append("3").append(first).append(' ');
      start = 1;
    }
    for (int i = start; i < value.length(); i++) {
      char c = value.charAt(i);
      if (isCssSpecial(c)) {
        result.append('\\').append(c);
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /** Escape a value used inside a double-quoted CSS attribute selector, e.g. {@code [name="..."]}. */
  static String escapeAttributeValue(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder result = new StringBuilder(value.length() + 4);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' || c == '"') {
        result.append('\\');
      }
      result.append(c);
    }
    return result.toString();
  }

  /** Validate a bare CSS tag name; reject characters that would corrupt the selector. */
  static String validateTagName(String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("tag name cannot be blank");
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean valid = Character.isLetterOrDigit(c) || c == '-' || c == '_';
      if (!valid) {
        throw new IllegalArgumentException("Invalid CSS tag name: '" + value + "'");
      }
    }
    return value;
  }

  private static boolean isCssSpecial(char c) {
    return CSS_SPECIAL_CHARS.indexOf(c) >= 0;
  }
}
