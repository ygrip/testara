package io.github.ygrip.testara.ui.playwright.capability;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.page.Element;
import com.microsoft.playwright.ElementHandle;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class PlaywrightElementResolver {

  @SuppressWarnings({"rawtypes"})
  protected ElementHandle element(Element locator) {
    if (locator == null) {
      return null;
    }
    if (locator.getLocator() == null && locator.child() == null) {
      return null;
    }
    try {
      if (locator.getLocator() == null) {
        return (ElementHandle) locator.child().one();
      }
      return (ElementHandle) locator.one();
    } catch (Exception e) {
      log.warn("Unable to find element on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  protected ElementHandle child(Element locator) {
    if (locator == null || ObjectUtils.isEmpty(locator.child())) {
      return null;
    }
    try {
      return (ElementHandle) locator.child().one();
    } catch (Exception e) {
      log.warn("Unable to find child element on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<ElementHandle> children(Element locator) {
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
  protected List<ElementHandle> elements(Element locator) {
    if (locator == null) {
      return null;
    }
    if (locator.getLocator() == null && locator.child() == null) {
      return null;
    }
    try {
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
      .map(ElementHandle::textContent)
      .orElse(null);
  }

  @SuppressWarnings({"rawtypes"})
  protected String value(Element locator) {
    return Optional.ofNullable(element(locator))
      .map(el -> el.getAttribute("value"))
      .orElse(null);
  }

  @SuppressWarnings({"rawtypes"})
  protected String attribute(Element locator, String attributeName) {
    if (StringUtils.isBlank(attributeName)) {
      return null;
    }
    ElementHandle targetElement = element(locator);
    return Optional.ofNullable(targetElement)
      .map(el -> el.getAttribute(attributeName))
      .orElse(null);
  }
}
