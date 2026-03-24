package io.github.ygrip.testara.ui.playwright.capability;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.page.Element;

import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class PlaywrightElementResolver {
  protected final PlaywrightSession session;

  protected PlaywrightElementResolver(PlaywrightSession session) {
    this.session = session;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Element bindToSessionFinder(Element locator) {
    if (locator == null) {
      return null;
    }
    Element cursor = locator;
    while (cursor != null) {
      cursor.using(session.finder());
      cursor = cursor.child();
    }
    return locator;
  }

  /**
   * Resolve an element; must run on the Playwright API thread (call only inside
   * {@link PlaywrightSession#runOnApiThread}).
   */
  @SuppressWarnings({"rawtypes"})
  protected com.microsoft.playwright.Locator resolveOnApiThreadOnly(Element locator) {
    if (locator == null) {
      return null;
    }
    try {
      Element current = bindToSessionFinder(locator);
      while (current.child() != null) {
        current = current.child();
      }
      return (com.microsoft.playwright.Locator) current.one();
    } catch (Exception e) {
      log.warn("Unable to find element on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  protected com.microsoft.playwright.Locator resolveChildOnApiThreadOnly(Element locator) {
    if (locator == null || ObjectUtils.isEmpty(locator.child())) {
      return null;
    }
    try {
      Element current = bindToSessionFinder(locator).child();
      while (current.child() != null) {
        current = current.child();
      }
      return (com.microsoft.playwright.Locator) current.one();
    } catch (Exception e) {
      log.warn("Unable to find child element on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<com.microsoft.playwright.Locator> resolveChildrenOnApiThreadOnly(Element locator) {
    if (locator == null || ObjectUtils.isEmpty(locator.child())) {
      return null;
    }
    try {
      Element current = bindToSessionFinder(locator).child();
      while (current.child() != null) {
        current = current.child();
      }
      return current.all();
    } catch (Exception e) {
      log.warn("Unable to find child elements on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<com.microsoft.playwright.Locator> resolveElementsOnApiThreadOnly(Element locator) {
    if (locator == null) {
      return null;
    }
    try {
      Element current = bindToSessionFinder(locator);
      while (current.child() != null) {
        current = current.child();
      }
      return (List<com.microsoft.playwright.Locator>) current.all();
    } catch (Exception e) {
      log.warn("Unable to find elements on {}: {}", locator.getLocator(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  protected String text(Element locator) {
    return session.runOnApiThread(() -> Optional.ofNullable(resolveOnApiThreadOnly(locator))
      .map(com.microsoft.playwright.Locator::textContent)
      .orElse(null));
  }

  @SuppressWarnings({"rawtypes"})
  protected String value(Element locator) {
    return session.runOnApiThread(() -> Optional.ofNullable(resolveOnApiThreadOnly(locator))
      .map(com.microsoft.playwright.Locator::inputValue)
      .orElse(null));
  }

  @SuppressWarnings({"rawtypes"})
  protected String attribute(Element locator, String attributeName) {
    if (StringUtils.isBlank(attributeName)) {
      return null;
    }
    return session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      return Optional.ofNullable(targetElement)
        .map(el -> el.getAttribute(attributeName))
        .orElse(null);
    });
  }
}
