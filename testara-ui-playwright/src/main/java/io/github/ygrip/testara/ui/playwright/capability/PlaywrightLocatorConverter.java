package io.github.ygrip.testara.ui.playwright.capability;

import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Selector;

/**
 * Converts engine-agnostic {@link Locator} to Playwright-compatible CSS/XPath selector strings.
 */
public final class PlaywrightLocatorConverter {

  public static String toSelector(Locator locator) {
    if (locator == null) {
      throw new IllegalArgumentException("locator cannot be null");
    }
    Selector s = locator.getStrategy();
    String v = locator.getValue();
    return switch (s) {
      case ID -> "#" + v;
      case CSS -> v;
      case XPATH -> "xpath=" + v;
      case CLASS -> "." + v;
      case TAG -> v;
      case NAME -> "[name=\"" + v + "\"]";
      case LINKTEXT -> "text=" + v;
      case PARTIALLINK -> "text=" + v;
      default -> v;
    };
  }

  private PlaywrightLocatorConverter() {}
}
