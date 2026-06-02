package io.github.ygrip.testara.ui.model;

public final class LocatorValueEscaper {

  private LocatorValueEscaper() {
  }

  public static String escape(Selector selector, Object value) {
    if (value == null) {
      return "";
    }

    String raw = String.valueOf(value);

    return switch (selector) {
      case XPATH -> escapeForQuotedXPath(raw);
      case CSS -> escapeForQuotedCss(raw);
      default -> raw;
    };
  }

  /**
   * Placeholder is expected to be inside an XPath quoted literal:
   * xpath://button[normalize-space()='{label}']
   * Therefore this method does NOT add quotes.
   */
  static String escapeForQuotedXPath(String value) {
    if (value == null) {
      return "";
    }

    // Most templates use single quotes around placeholder.
    // Escape single quote by using XML/XPath-safe entity.
    // Selenium XPath can evaluate &apos; in XML-ish contexts, but browsers can be inconsistent.
    // Safer baseline: fail fast for single quote until typed placeholders are added.
    if (value.contains("'")) {
      throw new IllegalArgumentException(
        "XPath parameter value contains single quote, requires placeholder to be inside quotes, e.g. '{name}'. ");
    }

    return value;
  }

  /**
   * Placeholder is expected to be inside a CSS quoted attribute value:
   * css:[data-name='{productName}']
   * Therefore this method does NOT add quotes.
   */
  static String escapeForQuotedCss(String value) {
    if (value == null) {
      return "";
    }

    return value.replace("\\", "\\\\")
      .replace("'", "\\'");
  }
}
