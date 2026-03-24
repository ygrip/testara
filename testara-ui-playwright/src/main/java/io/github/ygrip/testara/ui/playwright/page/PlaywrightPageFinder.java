package io.github.ygrip.testara.ui.playwright.page;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.PageFinder;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightLocatorConverter;
import io.github.ygrip.testara.ui.playwright.config.PlaywrightDriverProperties;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import io.github.ygrip.testara.ui.populator.ElementCatalog;
import lombok.extern.log4j.Log4j2;

@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class PlaywrightPageFinder extends PageFinder<PlaywrightPage, com.microsoft.playwright.Locator, String> {

  private static PlaywrightSession currentPwSession() {
    PlaywrightSession onApi = PlaywrightSession.sessionRunningOnThisThread();
    if (onApi != null) {
      return onApi;
    }
    var d = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    if (d instanceof PlaywrightSession pws) {
      return pws;
    }
    for (DriverSession<?> candidate : DriverSessionManager.inThisTestThread()
      .getCurrentDrivers()) {
      if (candidate instanceof PlaywrightSession pws && candidate.isActive()) {
        return pws;
      }
    }
    return null;
  }

  private static PlaywrightSession requireSession(PlaywrightPage page) {
    if (page != null) {
      PlaywrightSession fromPage = page.driver();
      if (fromPage != null) {
        return fromPage;
      }
    }
    PlaywrightSession s = currentPwSession();
    if (s == null) {
      throw new IllegalStateException(
        "Playwright: set finder.setCurrentPage(...) after navigation, or register a Playwright driver on this test thread.");
    }
    return s;
  }

  /**
   * Normalize a selector string so Playwright's locator engine handles both CSS and XPath.
   * Raw XPath expressions (starting with /, ./, (, or containing ::) are prefixed with "xpath=".
   */
  private static String normalizeSelector(String locator) {
    if (locator == null) {
      return null;
    }
    if (locator.startsWith("xpath=")) {
      return locator;
    }
    if (looksLikeXpath(locator)) {
      return "xpath=" + locator;
    }
    // Strip leading "* > " — in standard CSS it's a no-op (every element is a child of *),
    // but in Playwright's chained locator context, * only matches strict descendants of the
    // parent, not the parent itself.  So "* > span.X" fails to find a span that is a direct
    // child of the scoped parent.  Removing the prefix makes the selector equivalent.
    return locator.replaceFirst("^\\*\\s*>\\s*", "");
  }

  private static boolean looksLikeXpath(String locator) {
    return locator.startsWith("/") || locator.startsWith("./") || locator.startsWith("(") || locator.contains("::");
  }

  @Override
  public Class<PlaywrightDriverProperties> configType() {
    return PlaywrightDriverProperties.class;
  }

  @Override
  public Logger log() {
    return log;
  }

  @Override
  public String getLocator(PlaywrightPage page, String element) {
    if (page == null || element == null || element.isBlank()) {
      return null;
    }

    String result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(com.microsoft.playwright.Locator.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            result = PlaywrightLocatorConverter.toSelector((Locator) value);
          } else if (key.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
            result = value.toString();
          }
        }
      } else {
        result = PlaywrightLocatorConverter.toSelector(resolveLocator(element));
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
  public String getLocator(Locator locator) {
    if (locator == null) {
      return null;
    }
    return PlaywrightLocatorConverter.toSelector(locator);
  }

  @Override
  public Supplier<com.microsoft.playwright.Locator> getElementFromPage(PlaywrightPage page, String element)
    throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<com.microsoft.playwright.Locator> result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(com.microsoft.playwright.Locator.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            Locator locator = (Locator) value;
            String selector = PlaywrightLocatorConverter.toSelector(locator);
            final PlaywrightSession s = requireSession(page);
            result = () -> s.runOnApiThread(() -> s.pageForApi()
              .locator(selector)
              .first());
          } else if (key.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
            final com.microsoft.playwright.Locator pwLoc = (com.microsoft.playwright.Locator) value;
            result = () -> pwLoc.first();
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
  public Supplier<List<com.microsoft.playwright.Locator>> getElementsFromPage(PlaywrightPage page, String element)
    throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<List<com.microsoft.playwright.Locator>> result = ArrayList::new;
    try {
      ElementCatalog catalog = buildPageCatalog(page);

      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(com.microsoft.playwright.Locator.class)
        .orBy(new TypeReference<List<com.microsoft.playwright.Locator>>() {
        })
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            Locator locator = (Locator) value;
            String selector = PlaywrightLocatorConverter.toSelector(locator);
            final PlaywrightSession s = requireSession(page);
            result = () -> s.runOnApiThread(() -> {
              com.microsoft.playwright.Locator base = s.pageForApi().locator(selector);
              base.first().waitFor();
              return base.all();
            });
          } else if (key.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
            final com.microsoft.playwright.Locator pwLoc = (com.microsoft.playwright.Locator) value;
            final PlaywrightSession s = requireSession(page);
            result = () -> s.runOnApiThread(() -> {
              pwLoc.first().waitFor();
              return pwLoc.all();
            });
          } else if (key.isCollectionLikeType()) {
            if (key.getContentType()
              .isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
              result = () -> MapperHelper.toObject(
                value, new TypeReference<>() {
                }
              );
            }
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
  public Supplier<List<com.microsoft.playwright.Locator>> getElementsFromPage(PlaywrightPage page, Locator locator)
    throws Exception {
    String selector = PlaywrightLocatorConverter.toSelector(locator);
    final PlaywrightSession s = requireSession(page);
    return () -> s.runOnApiThread(() -> {
      com.microsoft.playwright.Locator base = s.pageForApi().locator(selector);
      base.first().waitFor();
      return base.all();
    });
  }

  @Override
  public Supplier<com.microsoft.playwright.Locator> getElement(Locator locator) throws Exception {
    return getElementFromPage(getCurrentPage(), locator);
  }

  @Override
  public Supplier<List<com.microsoft.playwright.Locator>> getElements(Locator locator) throws Exception {
    return getElementsFromPage(getCurrentPage(), locator);
  }

  @Override
  public List<com.microsoft.playwright.Locator> getElementsWithRoot(com.microsoft.playwright.Locator parent,
    String locator) {
    try {
      if (parent == null || locator == null) {
        return new ArrayList<>();
      }

      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return new ArrayList<>();
      }

      return s.runOnApiThread(() -> parent.locator(normalizeSelector(locator)).all());
    } catch (Exception e) {
      log.error("Failed to get elements with root. Locator: {}, Error: {}", locator, e.getMessage());
      return new ArrayList<>();
    }
  }

  @Override
  public com.microsoft.playwright.Locator getElementWithRoot(com.microsoft.playwright.Locator parent, String locator) {
    try {
      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return null;
      }
      return s.runOnApiThread(() -> {
        if (ObjectUtils.isEmpty(locator)) {
          return parent;
        }
        com.microsoft.playwright.Locator result = parent.locator(normalizeSelector(locator));
        if (result.count() == 0) return null;
        return result.first();
      });
    } catch (Exception e) {
      log.error("Failed to get element with root. Locator: {}, Error: {}", locator, e.getMessage());
      return null;
    }
  }

  @Override
  public com.microsoft.playwright.Locator getPrecedingSiblingElement(com.microsoft.playwright.Locator parent,
    String locator) {
    try {
      com.microsoft.playwright.Locator element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return null;
      }
      return s.runOnApiThread(() -> {
        com.microsoft.playwright.Locator sibling = element.locator("xpath=preceding-sibling::*[1]");
        if (sibling.count() == 0) return null;
        return sibling;
      });
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public List<com.microsoft.playwright.Locator> getPrecedingSiblingElements(com.microsoft.playwright.Locator parent,
    String locator) {
    try {
      com.microsoft.playwright.Locator element = getElementWithRoot(parent, locator);
      if (element == null) {
        return new ArrayList<>();
      }

      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return new ArrayList<>();
      }

      return s.runOnApiThread(() -> element.locator("xpath=preceding-sibling::*").all());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  @Override
  public com.microsoft.playwright.Locator getFollowingSiblingElement(com.microsoft.playwright.Locator parent,
    String locator) {
    try {
      com.microsoft.playwright.Locator element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return null;
      }
      return s.runOnApiThread(() -> {
        com.microsoft.playwright.Locator sibling = element.locator("xpath=following-sibling::*[1]");
        if (sibling.count() == 0) return null;
        return sibling;
      });
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public List<com.microsoft.playwright.Locator> getFollowingSiblingElements(com.microsoft.playwright.Locator parent,
    String locator) {
    try {
      com.microsoft.playwright.Locator element = getElementWithRoot(parent, locator);
      if (element == null) {
        return new ArrayList<>();
      }

      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return new ArrayList<>();
      }

      return s.runOnApiThread(() -> element.locator("xpath=following-sibling::*").all());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  @Override
  public List<com.microsoft.playwright.Locator> getSiblings(com.microsoft.playwright.Locator parent, String locator) {
    try {
      com.microsoft.playwright.Locator element = getElementWithRoot(parent, locator);
      if (element == null) {
        return new ArrayList<>();
      }

      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return new ArrayList<>();
      }

      return s.runOnApiThread(() -> element.locator("xpath=../child::*").all());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  @Override
  public com.microsoft.playwright.Locator getChildNode(com.microsoft.playwright.Locator parent, String locator,
    int childIndex) {
    try {
      com.microsoft.playwright.Locator element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }

      PlaywrightSession s = currentPwSession();
      if (s == null) {
        return null;
      }

      return s.runOnApiThread(() -> {
        com.microsoft.playwright.Locator children = element.locator(":scope > *");
        if (children.count() <= childIndex) return null;
        return children.nth(childIndex);
      });
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public Supplier<com.microsoft.playwright.Locator> getElementFromPage(PlaywrightPage page, Locator locator)
    throws Exception {
    String selector = PlaywrightLocatorConverter.toSelector(locator);
    final PlaywrightSession s = requireSession(page);
    return () -> s.runOnApiThread(() -> s.pageForApi()
      .locator(selector)
      .first());
  }

  @Override
  protected BiFunction<Field, Object, ElementCatalog> resolveElementStrategy(ElementCatalog catalog) {
    return (field, value) -> {
      final var javaType = MapperHelper.getGenericType(field);
      if (javaType.isTypeOrSubTypeOf(Locator.class)) {
        catalog.addElement(field.getName(), (Locator) value)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
        catalog.addElement(field.getName(), (com.microsoft.playwright.Locator) value)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isCollectionLikeType()) {
        if (javaType.getContentType()
          .isTypeOrSuperTypeOf(com.microsoft.playwright.Locator.class)) {
          catalog.addLazyElement(
              field.getName(), new TypeReference<List<com.microsoft.playwright.Locator>>() {
              }, field
            )
            .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
        }
      }
      return catalog;
    };
  }
}
