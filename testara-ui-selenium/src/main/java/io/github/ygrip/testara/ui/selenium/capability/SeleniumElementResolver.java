package io.github.ygrip.testara.ui.selenium.capability;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebElement;

import io.github.ygrip.testara.ui.page.Element;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class SeleniumElementResolver {
  @SuppressWarnings({"rawtypes"})
  protected WebElement element(Element locator) {
    if (locator == null) {
      return null;
    }
    if (locator.getLocator() == null && locator.child() == null) {
      return null;
    }
    try {
      // When element is "root.withChild(inner)" observation passes the root (no locator); resolve using child under root
      if (locator.getLocator() == null) {
        return (WebElement) locator.child().one();
      }
      return (WebElement) locator.one();
    } catch (Exception e) {
      log.warn("Unable to find element on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  protected WebElement child(Element locator) {
    if (locator == null || ObjectUtils.isEmpty(locator.child())) {
      return null;
    }
    try {
      return (WebElement) locator.child().one();
    } catch (Exception e) {
      log.warn("Unable to find child element on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<WebElement> children(Element locator) {
    if (locator == null || ObjectUtils.isEmpty(locator.child())) {
      return null;
    }
    try {
      return locator.child().all();
    } catch (Exception e) {
      log.warn("Unable to find child elements on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<WebElement> elements(Element locator) {
    if (locator == null) {
      return null;
    }
    if (locator.getLocator() == null && locator.child() == null) {
      return null;
    }
    try {
      // When element is "root.withChild(inner)" observation passes the root (no locator); resolve using child under root
      if (locator.getLocator() == null) {
        return locator.child().all();
      }
      return locator.all();
    } catch (Exception e) {
      log.warn("Unable to find elements on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  protected String text(Element locator) {
    return Optional.ofNullable(element(locator))
      .map(WebElement::getText)
      .orElse(null);
  }

  @SuppressWarnings({"rawtypes"})
  protected String value(Element locator) {
    return Optional.ofNullable(element(locator))
      .map(val -> val.getDomAttribute("value"))
      .orElse(null);
  }

  @SuppressWarnings({"rawtypes"})
  protected String attribute(Element locator, String attributeName) {
    if (StringUtils.isBlank(attributeName)) {
      return null;
    }
    WebElement targetElement = element(locator);
    return Optional.ofNullable(targetElement)
      .map(attr -> attr.getDomAttribute(attributeName))
      .orElse(null);
  }
}
