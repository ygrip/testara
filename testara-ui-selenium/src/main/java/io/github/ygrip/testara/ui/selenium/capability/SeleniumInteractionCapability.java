package io.github.ygrip.testara.ui.selenium.capability;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.page.Element;

import lombok.extern.log4j.Log4j2;

/**
 * Selenium implementation of {@link InteractionCapability}. Fluent, chainable.
 */
@Log4j2
public final class SeleniumInteractionCapability extends SeleniumElementResolver implements InteractionCapability {
  private final WebDriver driver;

  public SeleniumInteractionCapability(WebDriver driver) {
    this.driver = driver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScript(String script, Object... args) {
    JavascriptExecutor js = (JavascriptExecutor) driver;
    return (T) js.executeScript(script, args);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScriptAsync(String script, Object... args) {
    JavascriptExecutor js = (JavascriptExecutor) driver;
    return (T) js.executeAsyncScript(script, args);
  }

  @Override
  public InteractionCapability scrollTo(Element locator, boolean alignToTop) {
    WebElement target = element(locator);
    if(ObjectUtils.isNotEmpty(target)){
      executeScript("arguments[0].scrollIntoView(arguments[1]);", target, alignToTop);
    }
    return this;
  }

  @Override
  public InteractionCapability click(Element locator) {
    WebElement targetElement = element(locator);
    if(ObjectUtils.isNotEmpty(targetElement)){
      targetElement.click();
    }
    return this;
  }

  @Override
  public InteractionCapability focus(Element locator) {
    WebElement targetElement = element(locator);
    if(ObjectUtils.isNotEmpty(targetElement)){
      executeScript("arguments[0].focus()", targetElement);
    }
    return this;
  }

  @Override
  public InteractionCapability blur(Element locator) {
    WebElement targetElement = element(locator);
    if(ObjectUtils.isNotEmpty(targetElement)){
      executeScript("arguments[0].blur()", targetElement);
    }
    return this;
  }

  @Override
  public InteractionCapability forceClick(Element locator) {
    WebElement target = element(locator);
    if(ObjectUtils.isNotEmpty(target)){
      executeScript("arguments[0].click();", target);
    }
    return this;
  }

  @Override
  public InteractionCapability doubleClick(Element locator) {
    WebElement targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      new Actions(driver).doubleClick(targetElement)
        .build()
        .perform();
    }

    return this;
  }

  @Override
  public InteractionCapability hover(Element locator) {
    WebElement targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      new Actions(driver).moveToElement(targetElement)
        .build()
        .perform();
    }
    return this;
  }

  @Override
  public InteractionCapability hold(Element locator, Duration duration) {
    WebElement targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      new Actions(driver).clickAndHold(targetElement)
        .pause(duration)
        .release()
        .build()
        .perform();
    }
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, Element target) {
    WebElement sourceElement = element(source);
    WebElement targetElement = element(target);
    if (ObjectUtils.isNotEmpty(targetElement) && ObjectUtils.isNotEmpty(sourceElement)) {
      new Actions(driver).dragAndDrop(sourceElement, targetElement)
        .build()
        .perform();
    }
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, int xOffset, int yOffset) {
    WebElement sourceElement = element(source);
    if (ObjectUtils.isNotEmpty(sourceElement)) {
      Actions actions = new Actions(driver);
      actions.clickAndHold(sourceElement)
        .moveByOffset(xOffset, yOffset)
        .release()
        .build()
        .perform();
    }

    return this;
  }

  @Override
  public TextEntry enter(String text) {
    return locator -> {
      WebElement el = element(locator);
      if (ObjectUtils.isNotEmpty(el)) {
        el.clear();
        el.sendKeys(text);
      }
      return SeleniumInteractionCapability.this;
    };
  }

  @Override
  public InteractionCapability clear(Element locator) {
    WebElement targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.clear();
    }
    return this;
  }

  @Override
  public InteractionCapability submit(Element locator) {
    WebElement targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.submit();
    }
    return this;
  }

  @Override
  public SelectOption selectOption(Element locator) {
    return new SelectOption() {
      private Select resolveSelect() {
        WebElement el = element(locator);
        if (ObjectUtils.isEmpty(el)) {
          return null;
        }
        return new Select(el);
      }

      @Override
      public InteractionCapability byValue(String value) {
        Select select = resolveSelect();
        if (select != null) {
          select.selectByValue(value);
        }
        return SeleniumInteractionCapability.this;
      }

      @Override
      public InteractionCapability byIndex(int index) {
        Select select = resolveSelect();
        if (select != null) {
          select.selectByIndex(index);
        }
        return SeleniumInteractionCapability.this;
      }

      @Override
      public InteractionCapability byVisibleText(String visibleText) {
        Select select = resolveSelect();
        if (select != null) {
          select.selectByVisibleText(visibleText);
        }
        return SeleniumInteractionCapability.this;
      }
    };
  }

  @Override
  public WebElement findElement(Element locator) {
    try {
      return element(locator);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public List<WebElement> findElements(Element locator) {
    try {
      return elements(locator);
    } catch (Exception e) {
      return List.of();
    }
  }

}
