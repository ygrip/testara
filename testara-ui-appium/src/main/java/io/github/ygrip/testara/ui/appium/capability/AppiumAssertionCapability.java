package io.github.ygrip.testara.ui.appium.capability;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;

import io.appium.java_client.AppiumDriver;
import lombok.extern.log4j.Log4j2;

/**
 * Appium implementation of {@link AssertionCapability}. Fluent, fail-fast.
 */
@Log4j2
public final class AppiumAssertionCapability extends AppiumElementResolver implements AssertionCapability {
  private final AppiumDriver driver;

  public AppiumAssertionCapability(AppiumDriver driver) {
    this.driver = driver;
  }

  @Override
  public AssertionCapability seeThatVisible(Element locator) {
    if (!isVisible(locator)) {
      throw new AssertionError("Element not visible: " + locator.getLocator());
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatHidden(Element locator) {
    if (!isHidden(locator)) {
      throw new AssertionError("Element not hidden: " + locator.getLocator());
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatAttribute(Element locator, String attributeName) {
    Boolean valid = Optional.ofNullable(element(locator))
      .map(attr -> attr.getDomAttribute(attributeName))
      .map(StringUtils::isNotBlank)
      .orElse(false);
    if (!valid) {
      throw new AssertionError(
        "Element : " + locator.getLocator() + " does not have attribute name : " + attributeName);
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatText(Element locator, String expectedText) {
    String actual = text(locator);
    if (!expectedText.equals(actual)) {
      throw new AssertionError("Expected text: '" + expectedText + "', actual: '" + actual + "'");
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatValue(Element locator, String value) {
    String actual = value(locator);
    if (!value.equals(actual)) {
      throw new AssertionError("Expected value: '" + value + "', actual: '" + actual + "'");
    }
    return this;
  }

  @Override
  public AssertionCapability containsThatValue(Element locator, String value) {
    String actual = value(locator);
    if (!value.contains(actual)) {
      throw new AssertionError("Expected value to contain: '" + value + "', actual: '" + actual + "'");
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatContainsText(Element locator, String substring) {
    String actual = text(locator);
    if (actual == null || !actual.contains(substring)) {
      throw new AssertionError("Expected to contain: '" + substring + "', actual: '" + actual + "'");
    }
    return this;
  }

  @Override
  public AssertionCapability hasClass(Element locator, String className) {
    String attribute = attribute(locator, className);
    if(attribute == null || !attribute.contains(className)){
      throw new AssertionError("Expected to contain class: '" + className + "', actual: '" + attribute + "'");
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatPresent(Element locator) {
    if (!isPresent(locator)) {
      throw new AssertionError("Element not present: " + locator.getLocator());
    }
    return this;
  }

  @Override
  public boolean isVisible(Element locator) {
    try {
      return Optional.ofNullable(element(locator))
        .map(WebElement::isDisplayed)
        .orElse(false);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isHidden(Element locator) {
    return !isVisible(locator);
  }

  @Override
  public boolean isEnabled(Element locator) {
    try {
      return Optional.ofNullable(element(locator))
        .map(WebElement::isEnabled)
        .orElse(false);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isPresent(Element locator) {
    try {
      return ObjectUtils.isNotEmpty(element(locator));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public AssertionCapability isOn(NamedPage namedPage) {
    var pageContext = namedPage.getPage();
    if (ObjectUtils.isNotEmpty(pageContext)) {
      if (!pageContext.isCurrentPage()) {
        throw new AssertionError("Page " + pageContext.metadata()
          .name() + " is not current page");
      }
      namedPage.getFinder().setCurrentPage(pageContext);
    } else {
      throw new AssertionError("Page "+ namedPage.getName() + "not found");
    }
    return this;
  }

}
