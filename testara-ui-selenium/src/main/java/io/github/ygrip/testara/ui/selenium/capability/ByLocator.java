package io.github.ygrip.testara.ui.selenium.capability;

import org.openqa.selenium.By;

import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Selector;

/** Converts engine-agnostic {@link Locator} to Selenium {@link By}. */
public final class ByLocator {

  public static By toBy(Locator locator) {
    if (locator == null) {
      throw new IllegalArgumentException("locator cannot be null");
    }
    Selector s = locator.getStrategy();
    String v = locator.resolvedValue();
    return switch (s) {
      case ID -> By.id(v);
      case CSS -> By.cssSelector(v);
      case XPATH -> By.xpath(v);
      case CLASS -> By.className(v);
      case TAG -> By.tagName(v);
      case NAME -> By.name(v);
      case LINKTEXT -> By.linkText(v);
      case PARTIALLINK -> By.partialLinkText(v);
      default -> By.cssSelector(v);
    };
  }

  private ByLocator() {}
}
