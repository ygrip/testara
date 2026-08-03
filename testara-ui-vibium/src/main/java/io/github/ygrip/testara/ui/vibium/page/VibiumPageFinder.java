package io.github.ygrip.testara.ui.vibium.page;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JavaType;

import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.PageFinder;
import io.github.ygrip.testara.ui.populator.ElementCatalog;
import io.github.ygrip.testara.ui.vibium.config.VibiumDriverProperties;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;
import io.github.ygrip.testara.ui.vibium.locator.VibiumLocatorConverter;
import io.github.ygrip.testara.ui.vibium.locator.VibiumSelector;

import lombok.extern.log4j.Log4j2;

/**
 * Vibium's {@link PageFinder}. Session-owned: {@link VibiumSession#finder()} constructs exactly
 * one instance per session (never shared/rebuilt across sessions — see the session's finder()
 * javadoc for why this deliberately does not mirror Playwright's shared, test-scope-cached
 * finder), calling {@link #bindSession(DriverSession)} and {@link #setDeviceType} once.
 *
 * <p>Only CSS-derived locators (ID/CSS/CLASS/TAG/NAME) resolve to an interaction-safe
 * {@link VibiumElement}; XPATH/LINKTEXT/PARTIALLINK and the semantic {@code VibiumLocator} builder
 * are discovery-only (see {@link VibiumLocatorConverter}).
 */
@Log4j2
public class VibiumPageFinder extends PageFinder<VibiumPage, VibiumElement, VibiumSelector> {

  private static VibiumSession currentVibiumSession() {
    DriverSession<?> current = DriverSessionManager.inThisTestThread().getCurrentDriver();
    if (current instanceof VibiumSession vibiumSession) {
      return vibiumSession;
    }
    for (DriverSession<?> candidate : DriverSessionManager.inThisTestThread().getCurrentDrivers()) {
      if (candidate instanceof VibiumSession vibiumSession && candidate.isActive()) {
        return vibiumSession;
      }
    }
    return null;
  }

  private static VibiumSession requireSession(VibiumPage page) {
    if (page != null) {
      VibiumSession fromPage = page.driver();
      if (fromPage != null) {
        return fromPage;
      }
    }
    VibiumSession session = currentVibiumSession();
    if (session == null) {
      throw new IllegalStateException(
        "Vibium: set finder.setCurrentPage(...) after navigation, or register a Vibium driver on this test thread."
      );
    }
    return session;
  }

  @Override
  public Class<VibiumDriverProperties> configType() {
    return VibiumDriverProperties.class;
  }

  @Override
  public Logger log() {
    return log;
  }

  @Override
  public VibiumSelector getLocator(VibiumPage page, String element) {
    if (page == null || element == null || element.isBlank()) {
      return null;
    }

    VibiumSelector result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(VibiumSelector.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            result = VibiumLocatorConverter.toSelector((Locator) value);
          } else if (key.isTypeOrSubTypeOf(VibiumSelector.class)) {
            result = (VibiumSelector) value;
          }
        }
      } else {
        result = VibiumLocatorConverter.toSelector(resolveLocator(element));
      }
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log.warn(
          "Locator lookup failed for '{}': {} (page={})",
          element,
          e.getMessage(),
          page.getClass()
            .getSimpleName(),
          e
        );
      }
    }
    return result;
  }

  @Override
  public VibiumSelector getLocator(VibiumPage page, String element, Map<String, ?> parameters) {
    if (page == null || element == null || element.isBlank()) {
      return null;
    }

    VibiumSelector result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(VibiumSelector.class)
        .withQuery(element)
        .withParameters(parameters)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            result = VibiumLocatorConverter.toSelector((Locator) value);
          } else if (key.isTypeOrSubTypeOf(VibiumSelector.class)) {
            result = (VibiumSelector) value;
          }
        }
      }
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log.warn("Locator lookup failed for '{}': {} (page={})", element, e.getMessage(),
          page.getClass().getSimpleName(), e);
      }
    }
    return result;
  }

  @Override
  public VibiumSelector getLocator(Locator locator) {
    if (locator == null) {
      return null;
    }
    return VibiumLocatorConverter.toSelector(locator);
  }

  @Override
  public Supplier<VibiumElement> getElementFromPage(VibiumPage page, String element) throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<VibiumElement> result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(VibiumSelector.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          VibiumSelector selector = null;
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            selector = VibiumLocatorConverter.toSelector((Locator) value);
          } else if (key.isTypeOrSubTypeOf(VibiumSelector.class)) {
            selector = (VibiumSelector) value;
          }
          if (selector != null) {
            final VibiumSelector finalSelector = selector;
            final VibiumSession session = requireSession(page);
            result = () -> finalSelector.find(session.pageForApi());
          }
        }
      }
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log.debug("Original lookup failed for '{}': {}", element, e.getMessage());
      }
    }
    return result;
  }

  @Override
  public Supplier<VibiumElement> getElementFromPage(VibiumPage page, Locator locator) throws Exception {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(locator);
    final VibiumSession session = requireSession(page);
    return () -> selector.find(session.pageForApi());
  }

  @Override
  public Supplier<List<VibiumElement>> getElementsFromPage(VibiumPage page, String element) throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<List<VibiumElement>> result = ArrayList::new;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(VibiumSelector.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          VibiumSelector selector = null;
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            selector = VibiumLocatorConverter.toSelector((Locator) value);
          } else if (key.isTypeOrSubTypeOf(VibiumSelector.class)) {
            selector = (VibiumSelector) value;
          }
          if (selector != null) {
            final VibiumSelector finalSelector = selector;
            final VibiumSession session = requireSession(page);
            result = () -> finalSelector.findAll(session.pageForApi());
          }
        }
      }
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log.debug("Original lookup failed for '{}': {}", element, e.getMessage());
      }
    }
    return result;
  }

  @Override
  public Supplier<List<VibiumElement>> getElementsFromPage(VibiumPage page, Locator locator) throws Exception {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(locator);
    final VibiumSession session = requireSession(page);
    return () -> selector.findAll(session.pageForApi());
  }

  @Override
  public Supplier<VibiumElement> getElement(Locator locator) throws Exception {
    return getElementFromPage(getCurrentPage(), locator);
  }

  @Override
  public Supplier<List<VibiumElement>> getElements(Locator locator) throws Exception {
    return getElementsFromPage(getCurrentPage(), locator);
  }

  @Override
  public List<VibiumElement> getElementsWithRoot(VibiumElement parent, VibiumSelector locator) {
    if (parent == null || locator == null) {
      return new ArrayList<>();
    }
    try {
      parent.requireInteractionSafe("getElementsWithRoot");
      return locator.findAll(parent.raw());
    } catch (Exception e) {
      log.error("Failed to get elements with root. Locator: {}, Error: {}", locator, e.getMessage());
      return new ArrayList<>();
    }
  }

  @Override
  public VibiumElement getElementWithRoot(VibiumElement parent, VibiumSelector locator) {
    if (parent == null) {
      return null;
    }
    try {
      parent.requireInteractionSafe("getElementWithRoot");
      if (locator == null) {
        return parent;
      }
      return locator.find(parent.raw());
    } catch (Exception e) {
      log.error("Failed to get element with root. Locator: {}, Error: {}", locator, e.getMessage());
      return null;
    }
  }

  @Override
  public VibiumElement getPrecedingSiblingElement(VibiumElement parent, VibiumSelector locator) {
    throw new UnsupportedVibiumCapabilityException(
      "getPrecedingSiblingElement",
      "Vibium's element-scoped find cannot reliably resolve a relative preceding-sibling xpath axis "
        + "(engine limitation); re-query the parent container with a CSS selector that targets the "
        + "sibling directly"
    );
  }

  @Override
  public List<VibiumElement> getPrecedingSiblingElements(VibiumElement parent, VibiumSelector locator) {
    throw new UnsupportedVibiumCapabilityException(
      "getPrecedingSiblingElements",
      "Vibium's element-scoped find cannot reliably resolve a relative preceding-sibling xpath axis "
        + "(engine limitation); re-query the parent container with a CSS selector that targets the "
        + "siblings directly"
    );
  }

  @Override
  public VibiumElement getFollowingSiblingElement(VibiumElement parent, VibiumSelector locator) {
    throw new UnsupportedVibiumCapabilityException(
      "getFollowingSiblingElement",
      "Vibium's element-scoped find cannot reliably resolve a relative following-sibling xpath axis "
        + "(engine limitation); re-query the parent container with a CSS selector that targets the "
        + "sibling directly"
    );
  }

  @Override
  public List<VibiumElement> getFollowingSiblingElements(VibiumElement parent, VibiumSelector locator) {
    throw new UnsupportedVibiumCapabilityException(
      "getFollowingSiblingElements",
      "Vibium's element-scoped find cannot reliably resolve a relative following-sibling xpath axis "
        + "(engine limitation); re-query the parent container with a CSS selector that targets the "
        + "siblings directly"
    );
  }

  @Override
  public List<VibiumElement> getSiblings(VibiumElement parent, VibiumSelector locator) {
    // Same underlying engine limitation as the preceding/following-sibling axis: Vibium's
    // element-scoped find cannot reliably resolve a relative-position xpath query, and there is
    // no safe CSS equivalent for "siblings of X" without a full ancestor/descendant re-query.
    throw new UnsupportedVibiumCapabilityException(
      "getSiblings",
      "Vibium's element-scoped find cannot reliably resolve a relative sibling-axis query (engine "
        + "limitation); re-query the parent container directly with a CSS selector"
    );
  }

  @Override
  public VibiumElement getChildNode(VibiumElement parent, VibiumSelector locator, int childIndex) {
    if (parent == null) {
      return null;
    }
    try {
      parent.requireInteractionSafe("getChildNode");
      List<VibiumElement> children = VibiumSelector.css(":scope > *").findAll(parent.raw());
      if (children.size() <= childIndex) {
        return null;
      }
      return children.get(childIndex);
    } catch (Exception e) {
      log.error("Failed to get child node. childIndex: {}, Error: {}", childIndex, e.getMessage());
      return null;
    }
  }

  @Override
  protected BiFunction<Field, Object, ElementCatalog> resolveElementStrategy(ElementCatalog catalog) {
    return (field, value) -> {
      final var javaType = MapperHelper.getGenericType(field);
      if (javaType.isTypeOrSubTypeOf(Locator.class)) {
        catalog.addElement(field.getName(), (Locator) value)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isTypeOrSubTypeOf(VibiumSelector.class)) {
        catalog.addElement(field.getName(), (VibiumSelector) value)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      }
      return catalog;
    };
  }
}
