package io.github.ygrip.testara.ui.selenium.page;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsDriver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.PageFinder;
import io.github.ygrip.testara.ui.populator.ElementCatalog;
import io.github.ygrip.testara.ui.selenium.capability.ByLocator;
import io.github.ygrip.testara.ui.selenium.config.SeleniumDriverProperties;

import lombok.extern.log4j.Log4j2;

@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class SeleniumPageFinder extends PageFinder<SeleniumPage, WebElement, By> {

  @Override
  public Class<SeleniumDriverProperties> configType() {
    return SeleniumDriverProperties.class;
  }

  @Override
  public Logger log() {
    return log;
  }

  @Override
  public By getLocator(SeleniumPage page, String element) {
    if (page == null) {
      return null;
    }
    if (element == null || element.isBlank()) {
      return null;
    }

    By result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(By.class)
        .orBy(WebElement.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            final var locator = (Locator) value;
            result = ByLocator.toBy(locator);
          } else if (key.isTypeOrSubTypeOf(By.class)) {
            result = (By) value;
          } else if (key.isTypeOrSubTypeOf(WebElement.class)) {
            result = toLocator((WebElement) value);
          }
        }
      } else {
        result = ByLocator.toBy(resolveLocator(element));
      }
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log.warn(
          "Locator lookup failed for '{}': {} (page={})",
          element,
          e.getMessage(),
          page != null ?
            page.getClass()
              .getSimpleName() :
            "null",
          e
        );
      }
    }
    return result;
  }

  @Override
  public By getLocator(SeleniumPage page, String element, Map<String, ?> parameters) {
    if (page == null || element == null || element.isBlank()) {
      return null;
    }

    By result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(By.class)
        .orBy(WebElement.class)
        .withQuery(element)
        .withParameters(parameters)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            result = ByLocator.toBy((Locator) value);
          } else if (key.isTypeOrSubTypeOf(By.class)) {
            result = (By) value;
          } else if (key.isTypeOrSubTypeOf(WebElement.class)) {
            result = toLocator((WebElement) value);
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
  public By getLocator(Locator locator) {
    if (locator == null) {
      return null;
    }
    return ByLocator.toBy(locator);
  }

  private By toLocator(WebElement element) {
    By result = null;

    try {
      String source = String.valueOf(element);
      source = source.replaceFirst("By.", "");
      String[] path = source.split(":");
      String selector = path[0].trim()
        .replace("-", "");
      switch (selector) {
        case "id":
          result = By.id(path[1].trim());
          break;
        case "cssSelector":
          result = By.cssSelector(path[1].trim());
          break;
        case "xpath":
          result = By.xpath(path[1].trim());
          break;
        case "className":
          result = By.className(path[1].trim());
          break;
        case "tagName":
          result = By.tagName(path[1].trim());
          break;
        case "linkText":
          result = By.linkText(path[1].trim());
          break;
        case "partialLinkText":
          result = By.partialLinkText(path[1].trim());
          break;
        case "name":
          result = By.name(path[1].trim());
          break;
      }
    } catch (Exception var5) {
    }

    return result;
  }

  @Override
  public Supplier<WebElement> getElementFromPage(SeleniumPage page, String element) throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<WebElement> result = null;
    try {
      ElementCatalog catalog = buildPageCatalog(page);
      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(By.class)
        .orBy(WebElement.class)
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            Locator locator = (Locator) value;
            result = () -> page.driver()
              .instance()
              .findElement(ByLocator.toBy(locator));
          } else if (key.isTypeOrSubTypeOf(By.class)) {
            By locator = (By) value;
            result = () -> page.driver()
              .instance()
              .findElement(locator);
          } else if (key.isTypeOrSubTypeOf(WebElement.class)) {
            result = () -> (WebElement) value;
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
  public Supplier<List<WebElement>> getElementsFromPage(SeleniumPage page, String element) throws Exception {
    if (page == null) {
      return null;
    }
    Supplier<List<WebElement>> result = ArrayList::new;
    try {
      ElementCatalog catalog = buildPageCatalog(page);

      Map.Entry<JavaType, Object> item = catalog.findBy(Locator.class)
        .orBy(By.class)
        .orBy(new TypeReference<List<WebElement>>() {
        })
        .withQuery(element)
        .getResult(page);

      if (ObjectUtils.isNotEmpty(item)) {
        final var key = item.getKey();
        var value = item.getValue();
        if (ObjectUtils.isNotEmpty(value)) {
          if (key.isTypeOrSubTypeOf(Locator.class)) {
            Locator locator = (Locator) value;
            result = () -> page.driver()
              .instance()
              .findElements(ByLocator.toBy(locator));
          } else if (key.isTypeOrSubTypeOf(By.class)) {
            By locator = (By) value;
            result = () -> page.driver()
              .instance()
              .findElements(locator);
          } else if (key.isCollectionLikeType()) {
            if (key.getContentType()
              .isTypeOrSubTypeOf(WebElement.class)) {
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
  public Supplier<List<WebElement>> getElementsFromPage(SeleniumPage page, Locator locator) throws Exception {
    return () -> page.driver()
      .instance()
      .findElements(ByLocator.toBy(locator));
  }

  @Override
  public Supplier<WebElement> getElement(Locator locator) throws Exception {
    return getElementFromPage(getCurrentPage(), locator);
  }

  @Override
  public Supplier<List<WebElement>> getElements(Locator locator) throws Exception {
    return getElementsFromPage(getCurrentPage(), locator);
  }

  @Override
  public List<WebElement> getElementsWithRoot(WebElement parent, By locator) {
    try {
      return parent.findElements(locator);
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  @Override
  public WebElement getElementWithRoot(WebElement parent, By locator) {
    try {
      return ObjectUtils.isNotEmpty(locator) ? parent.findElement(locator) : parent;
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public WebElement getPrecedingSiblingElement(WebElement parent, By locator) {
    try {
      WebElement element = getElementWithRoot(parent, locator);
      WebDriver driver = ((WrapsDriver) element).getWrappedDriver();
      return (WebElement) ((JavascriptExecutor) driver).executeScript(
        "return arguments[0].previousElementSibling;",
        element
      );
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<WebElement> getPrecedingSiblingElements(WebElement parent, By locator) {
    try {
      WebElement element = getElementWithRoot(parent, locator);
      WebDriver driver = ((WrapsDriver) element).getWrappedDriver();
      final var script = """
        function getPrecedingSiblings(elem) {
             var siblings = [];
             var sibling = elem.previousElementSibling;
        
             while (sibling) {
                 siblings.push(sibling);
        
                 sibling = sibling.previousElementSibling;
             }
        
             return siblings;
         }
        
        return getPrecedingSiblings(arguments[0]);
        """;
      return (List<WebElement>) ((JavascriptExecutor) driver).executeScript(script, element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public WebElement getFollowingSiblingElement(WebElement parent, By locator) {
    try {
      WebElement element = getElementWithRoot(parent, locator);
      WebDriver driver = ((WrapsDriver) element).getWrappedDriver();
      return (WebElement) ((JavascriptExecutor) driver).executeScript("return arguments[0].nextSibling;", element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<WebElement> getFollowingSiblingElements(WebElement parent, By locator) {
    try {
      WebElement element = getElementWithRoot(parent, locator);
      WebDriver driver = ((WrapsDriver) element).getWrappedDriver();
      final var script = """
        function getFollowingSiblings(elem) {
          var siblings = [];
          var sibling = elem.nextElementSibling;
        
          while (sibling) {
            siblings.push(sibling);
            sibling = sibling.nextElementSibling;
          }
        
          return siblings;
        }
        
        return getFollowingSiblings(arguments[0]);
        """;
      return (List<WebElement>) ((JavascriptExecutor) driver).executeScript(script, element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<WebElement> getSiblings(WebElement parent, By locator) {
    try {
      WebElement element = getElementWithRoot(parent, locator);
      WebDriver driver = ((WrapsDriver) element).getWrappedDriver();
      final var script = """
        function getSiblings(elem) {
          var siblings = [];
          var sibling = elem.parentNode.firstChild;
        
          while (sibling) {
            if (sibling.nodeType === 1 && sibling !== elem) {
              siblings.push(sibling);
            }
            sibling = sibling.nextSibling;
          }
        
          return siblings;
        }
        
        return getSiblings(arguments[0]);
        """;
      return (List<WebElement>) ((JavascriptExecutor) driver).executeScript(script, element);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public WebElement getChildNode(WebElement parent, By locator, int childIndex) {
    try {
      WebElement element = getElementWithRoot(parent, locator);
      WebDriver driver = ((WrapsDriver) element).getWrappedDriver();
      return (WebElement) ((JavascriptExecutor) driver).executeScript(
        String.format("return arguments[0].childNodes[%s];",
          childIndex
        ), element
      );
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public Supplier<WebElement> getElementFromPage(SeleniumPage page, Locator locator) throws Exception {
    return () -> page.driver()
      .instance()
      .findElement(ByLocator.toBy(locator));
  }

  @Override
  protected BiFunction<Field, Object, ElementCatalog> resolveElementStrategy(ElementCatalog catalog) {
    return (field, value) -> {
      final var javaType = MapperHelper.getGenericType(field);
      if (javaType.isTypeOrSubTypeOf(Locator.class)) {
        catalog.addElement(field.getName(), (Locator) value)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isTypeOrSubTypeOf(By.class)) {
        catalog.addElement(field.getName(), (By) value)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isTypeOrSubTypeOf(WebElement.class)) {
        catalog.addLazyElement(field.getName(), WebElement.class, field)
          .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
      } else if (javaType.isCollectionLikeType()) {
        if (javaType.getContentType()
          .isTypeOrSuperTypeOf(WebElement.class)) {
          catalog.addLazyElement(
              field.getName(), new TypeReference<List<WebElement>>() {
              }, field
            )
            .ifPresent(el -> el.setAliases(generateAliases(field.getName())));
        }
      }
      return catalog;
    };
  }
}
