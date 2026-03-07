package io.github.ygrip.testara.ui.appium.capability;

import org.openqa.selenium.By;

import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Selector;

import io.appium.java_client.AppiumBy;

/**
 * Converts engine-agnostic {@link Locator} to Selenium {@link AppiumBy}.
 */
public final class AppiumByLocator {

  private AppiumByLocator() {
  }

  public static By toBy(Locator locator) {
    if (locator == null) {
      throw new IllegalArgumentException("locator cannot be null");
    }
    Selector s = locator.getStrategy();
    String v = locator.getValue();
    return switch (s) {
      case ID -> AppiumBy.id(v);
      case CSS -> AppiumBy.cssSelector(v);
      case XPATH -> AppiumBy.xpath(v);
      case CLASS -> AppiumBy.className(v);
      case TAG -> AppiumBy.tagName(v);
      case NAME -> AppiumBy.name(v);
      case LINKTEXT -> AppiumBy.linkText(v);
      case PARTIALLINK -> AppiumBy.partialLinkText(v);
      case ACCESSIBILITY -> AppiumBy.accessibilityId(v);
      case ANDROID_UI_AUTOMATOR -> AppiumBy.androidUIAutomator(v);
      case IOS_CLASS_CHAIN -> AppiumBy.iOSClassChain(v);
      default -> By.cssSelector(v);
    };
  }
}
