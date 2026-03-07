package io.github.ygrip.testara.ui.playwright.capability;

import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.page.Element;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightInteractionCapability extends PlaywrightElementResolver implements InteractionCapability {
  private final Page page;

  public PlaywrightInteractionCapability(Page page) {
    this.page = page;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScript(String script, Object... args) {
    return (T) page.evaluate(script, args.length == 1 ? args[0] : args);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScriptAsync(String script, Object... args) {
    return (T) page.evaluate(script, args.length == 1 ? args[0] : args);
  }

  @Override
  public InteractionCapability scrollTo(Element locator, boolean alignToTop) {
    ElementHandle target = element(locator);
    if (ObjectUtils.isNotEmpty(target)) {
      target.evaluate("(el, align) => el.scrollIntoView(align)", alignToTop);
    }
    return this;
  }

  @Override
  public InteractionCapability click(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.click();
    }
    return this;
  }

  @Override
  public InteractionCapability focus(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.focus();
    }
    return this;
  }

  @Override
  public InteractionCapability blur(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      executeScript("arguments[0].blur();", targetElement);
    }
    return this;
  }

  @Override
  public InteractionCapability forceClick(Element locator) {
    ElementHandle target = element(locator);
    if (ObjectUtils.isNotEmpty(target)) {
      target.click(new ElementHandle.ClickOptions().setForce(true));
    }
    return this;
  }

  @Override
  public InteractionCapability doubleClick(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.dblclick();
    }
    return this;
  }

  @Override
  public InteractionCapability hover(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.hover();
    }
    return this;
  }

  @Override
  public InteractionCapability hold(Element locator, Duration duration) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      var box = targetElement.boundingBox();
      if (box != null) {
        double x = box.x + box.width / 2;
        double y = box.y + box.height / 2;
        page.mouse().move(x, y);
        page.mouse().down();
        page.waitForTimeout(duration.toMillis());
        page.mouse().up();
      }
    }
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, Element target) {
    ElementHandle sourceElement = element(source);
    ElementHandle targetElement = element(target);
    if (ObjectUtils.isNotEmpty(sourceElement) && ObjectUtils.isNotEmpty(targetElement)) {
      var sourceBox = sourceElement.boundingBox();
      var targetBox = targetElement.boundingBox();
      if (sourceBox != null && targetBox != null) {
        double sx = sourceBox.x + sourceBox.width / 2;
        double sy = sourceBox.y + sourceBox.height / 2;
        double tx = targetBox.x + targetBox.width / 2;
        double ty = targetBox.y + targetBox.height / 2;
        page.mouse().move(sx, sy);
        page.mouse().down();
        page.mouse().move(tx, ty);
        page.mouse().up();
      }
    }
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, int xOffset, int yOffset) {
    ElementHandle sourceElement = element(source);
    if (ObjectUtils.isNotEmpty(sourceElement)) {
      var box = sourceElement.boundingBox();
      if (box != null) {
        double sx = box.x + box.width / 2;
        double sy = box.y + box.height / 2;
        page.mouse().move(sx, sy);
        page.mouse().down();
        page.mouse().move(sx + xOffset, sy + yOffset);
        page.mouse().up();
      }
    }
    return this;
  }

  @Override
  public TextEntry enter(String text) {
    return locator -> {
      ElementHandle el = element(locator);
      if (ObjectUtils.isNotEmpty(el)) {
        el.fill("");
        el.fill(text);
      }
      return PlaywrightInteractionCapability.this;
    };
  }

  @Override
  public InteractionCapability clear(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.fill("");
    }
    return this;
  }

  @Override
  public InteractionCapability submit(Element locator) {
    ElementHandle targetElement = element(locator);
    if (ObjectUtils.isNotEmpty(targetElement)) {
      targetElement.press("Enter");
    }
    return this;
  }

  @Override
  public SelectOption selectOption(Element locator) {
    return new SelectOption() {
      @Override
      public InteractionCapability byValue(String value) {
        ElementHandle el = element(locator);
        if (ObjectUtils.isNotEmpty(el)) {
          el.selectOption(new com.microsoft.playwright.options.SelectOption().setValue(value));
        }
        return PlaywrightInteractionCapability.this;
      }

      @Override
      public InteractionCapability byIndex(int index) {
        ElementHandle el = element(locator);
        if (ObjectUtils.isNotEmpty(el)) {
          el.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(index));
        }
        return PlaywrightInteractionCapability.this;
      }

      @Override
      public InteractionCapability byVisibleText(String visibleText) {
        ElementHandle el = element(locator);
        if (ObjectUtils.isNotEmpty(el)) {
          el.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(visibleText));
        }
        return PlaywrightInteractionCapability.this;
      }
    };
  }

  @Override
  public ElementHandle findElement(Element locator) {
    try {
      return element(locator);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public List<ElementHandle> findElements(Element locator) {
    try {
      return elements(locator);
    } catch (Exception e) {
      return List.of();
    }
  }
}
