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
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.PageFinder;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightLocatorConverter;
import io.github.ygrip.testara.ui.playwright.config.PlaywrightDriverProperties;
import io.github.ygrip.testara.ui.populator.ElementCatalog;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class PlaywrightPageFinder extends PageFinder<PlaywrightPage, ElementHandle, String> {

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
    if (page == null) {
      return null;
    }
    if (element == null || element.isBlank()) {
      return null;
    }

    String result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(com.microsoft.playwright.Locator.class)
        .orBy(ElementHandle.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            result = PlaywrightLocatorConverter.toSelector((Locator) value);
          } else if (key.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
            result = toLocator(((com.microsoft.playwright.Locator) value).elementHandle());
          } else if (key.isTypeOrSubTypeOf(ElementHandle.class)) {
            result = toLocator((ElementHandle) value);
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
          page.getClass().getSimpleName(),
          e
        );
      }
    }
    return result;
  }

  @Override
  public String getLocator(PlaywrightPage page, Locator locator) {
    if (page == null || locator == null) {
      return null;
    }
    return PlaywrightLocatorConverter.toSelector(locator);
  }

  private String toLocator(ElementHandle element) {
    if (element == null) {
      return null;
    }
    try {
      Object result = element.evaluate("""
        el => {
          if (el.id) return '#' + el.id;
          if (el.className && typeof el.className === 'string') {
            const cls = el.className.trim().split(/\\s+/).join('.');
            if (cls) return '.' + cls;
          }
          const tag = el.tagName.toLowerCase();
          const parent = el.parentElement;
          if (!parent) return tag;
          const siblings = Array.from(parent.children).filter(c => c.tagName === el.tagName);
          if (siblings.length === 1) return tag;
          const idx = siblings.indexOf(el) + 1;
          return tag + ':nth-child(' + idx + ')';
        }
        """);
      return result != null ? result.toString() : null;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public Supplier<ElementHandle> getElementFromPage(PlaywrightPage page, String element) throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<ElementHandle> result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(com.microsoft.playwright.Locator.class)
        .orBy(ElementHandle.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            Locator locator = (Locator) value;
            String selector = PlaywrightLocatorConverter.toSelector(locator);
            result = () -> page.page().locator(selector).elementHandle();
          } else if (key.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
            result = () -> ((com.microsoft.playwright.Locator) value).elementHandle();
          } else if (key.isTypeOrSubTypeOf(ElementHandle.class)) {
            result = () -> (ElementHandle) value;
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
  public Supplier<List<ElementHandle>> getElementsFromPage(PlaywrightPage page, String element) throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<List<ElementHandle>> result = ArrayList::new;
    try {
      ElementCatalog catalog = buildPageCatalog(page);

      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(com.microsoft.playwright.Locator.class)
        .orBy(new TypeReference<List<ElementHandle>>() {
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
            result = () -> page.page().locator(selector).elementHandles();
          } else if (key.isTypeOrSubTypeOf(com.microsoft.playwright.Locator.class)) {
            result = () -> ((com.microsoft.playwright.Locator) value).elementHandles();
          } else if (key.isCollectionLikeType()) {
            if (key.getContentType()
              .isTypeOrSubTypeOf(ElementHandle.class)) {
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
  public Supplier<List<ElementHandle>> getElementsFromPage(PlaywrightPage page, Locator locator) throws Exception {
    String selector = PlaywrightLocatorConverter.toSelector(locator);
    return () -> page.page().locator(selector).elementHandles();
  }

  @Override
  public List<ElementHandle> getElementsWithRoot(ElementHandle parent, String locator) {
    try {
      if (parent == null || locator == null) {
        return new ArrayList<>();
      }
      return parent.querySelectorAll(locator);
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  @Override
  public ElementHandle getElementWithRoot(ElementHandle parent, String locator) {
    try {
      return ObjectUtils.isNotEmpty(locator) ? parent.querySelector(locator) : parent;
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public ElementHandle getPrecedingSiblingElement(ElementHandle parent, String locator) {
    try {
      ElementHandle element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      return (ElementHandle) element.evaluateHandle(
        "el => el.previousElementSibling"
      ).asElement();
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ElementHandle> getPrecedingSiblingElements(ElementHandle parent, String locator) {
    try {
      ElementHandle element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      Page page = element.ownerFrame().page();
      return (List<ElementHandle>) page.evaluate("""
        el => {
          var siblings = [];
          var sibling = el.previousElementSibling;
          while (sibling) {
            siblings.push(sibling);
            sibling = sibling.previousElementSibling;
          }
          return siblings;
        }
        """, element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public ElementHandle getFollowingSiblingElement(ElementHandle parent, String locator) {
    try {
      ElementHandle element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      return (ElementHandle) element.evaluateHandle(
        "el => el.nextElementSibling"
      ).asElement();
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ElementHandle> getFollowingSiblingElements(ElementHandle parent, String locator) {
    try {
      ElementHandle element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      Page page = element.ownerFrame().page();
      return (List<ElementHandle>) page.evaluate("""
        el => {
          var siblings = [];
          var sibling = el.nextElementSibling;
          while (sibling) {
            siblings.push(sibling);
            sibling = sibling.nextElementSibling;
          }
          return siblings;
        }
        """, element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ElementHandle> getSiblings(ElementHandle parent, String locator) {
    try {
      ElementHandle element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      Page page = element.ownerFrame().page();
      return (List<ElementHandle>) page.evaluate("""
        el => {
          var siblings = [];
          var sibling = el.parentNode.firstChild;
          while (sibling) {
            if (sibling.nodeType === 1 && sibling !== el) {
              siblings.push(sibling);
            }
            sibling = sibling.nextSibling;
          }
          return siblings;
        }
        """, element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public ElementHandle getChildNode(ElementHandle parent, String locator, int childIndex) {
    try {
      ElementHandle element = getElementWithRoot(parent, locator);
      if (element == null) {
        return null;
      }
      return (ElementHandle) element.evaluateHandle(
        String.format("el => el.childNodes[%d]", childIndex)
      ).asElement();
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public Supplier<ElementHandle> getElementFromPage(PlaywrightPage page, Locator locator) throws Exception {
    String selector = PlaywrightLocatorConverter.toSelector(locator);
    return () -> page.page().locator(selector).elementHandle();
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
      } else if (javaType.isTypeOrSubTypeOf(ElementHandle.class)) {
        catalog.addLazyElement(field.getName(), ElementHandle.class, field)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isCollectionLikeType()) {
        if (javaType.getContentType()
          .isTypeOrSuperTypeOf(ElementHandle.class)) {
          catalog.addLazyElement(
              field.getName(), new TypeReference<List<ElementHandle>>() {
              }, field
            )
            .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
        }
      }
      return catalog;
    };
  }
}
