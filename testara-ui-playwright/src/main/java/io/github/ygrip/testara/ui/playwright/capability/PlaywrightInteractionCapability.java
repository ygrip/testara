package io.github.ygrip.testara.ui.playwright.capability;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.page.Element;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightInteractionCapability extends PlaywrightElementResolver implements InteractionCapability {

  public PlaywrightInteractionCapability(PlaywrightSession session) {
    super(session);
  }

  private static String wrapAsSeleniumScript(String script, boolean async) {
    String body = Optional.ofNullable(script)
      .orElse("")
      .replaceAll("\\barguments\\s*\\[", "seleniumArgs[");
    String prefix = async ? "async " : "";
    return prefix + "function(arg) {"
      + "const seleniumArgs = Array.isArray(arg) ? arg : (arg == null ? [] : [arg]);"
      + body
      + "\n}";
  }

  /**
   * Convert any Playwright Locator args to ElementHandle so they can be passed to page.evaluate().
   */
  private static Object[] resolveEvalArgs(Object[] args) {
    Object[] resolved = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof com.microsoft.playwright.Locator loc) {
        resolved[i] = loc.elementHandle();
      } else {
        resolved[i] = args[i];
      }
    }
    return resolved;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScript(String script, Object... args) {
    String wrappedScript = wrapAsSeleniumScript(script, false);
    return session.runOnApiThread(() -> {
      Object[] resolved = resolveEvalArgs(args);
      return (T) session.pageForApi()
        .evaluate(wrappedScript, resolved.length == 1 ? resolved[0] : resolved);
    });
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScriptAsync(String script, Object... args) {
    String wrappedScript = wrapAsSeleniumScript(script, true);
    return session.runOnApiThread(() -> {
      Object[] resolved = resolveEvalArgs(args);
      return (T) session.pageForApi()
        .evaluate(wrappedScript, resolved.length == 1 ? resolved[0] : resolved);
    });
  }

  @Override
  public InteractionCapability scrollTo(Element locator, boolean alignToTop) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator target = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(target)) {
        target.evaluate("(el, align) => el.scrollIntoView(align)", alignToTop);
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability click(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.click();
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability focus(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.focus();
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability blur(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.blur();
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability forceClick(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator target = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(target)) {
        target.click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true));
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability doubleClick(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.dblclick();
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability hover(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.hover();
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability hold(Element locator, Duration duration) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        var box = targetElement.boundingBox();
        Page page = session.pageForApi();
        if (box != null) {
          double x = box.x + box.width / 2;
          double y = box.y + box.height / 2;
          page.mouse().move(x, y);
          page.mouse().down();
          page.waitForTimeout(duration.toMillis());
          page.mouse().up();
        }
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, Element target) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator sourceElement = resolveOnApiThreadOnly(source);
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(target);
      if (ObjectUtils.isNotEmpty(sourceElement) && ObjectUtils.isNotEmpty(targetElement)) {
        sourceElement.dragTo(targetElement);
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, int xOffset, int yOffset) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator sourceElement = resolveOnApiThreadOnly(source);
      if (ObjectUtils.isNotEmpty(sourceElement)) {
        var box = sourceElement.boundingBox();
        Page page = session.pageForApi();
        if (box != null) {
          double sx = box.x + box.width / 2;
          double sy = box.y + box.height / 2;
          page.mouse().move(sx, sy);
          page.mouse().down();
          page.mouse().move(sx + xOffset, sy + yOffset);
          page.mouse().up();
        }
      }
      return null;
    });
    return this;
  }

  @Override
  public TextEntry enter(String text) {
    return locator -> {
      session.runOnApiThread(() -> {
        com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
        if (ObjectUtils.isNotEmpty(el)) {
          el.clear();
          el.fill(text);
        }
        return null;
      });
      return PlaywrightInteractionCapability.this;
    };
  }

  @Override
  public InteractionCapability clear(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.clear();
      }
      return null;
    });
    return this;
  }

  @Override
  public InteractionCapability submit(Element locator) {
    session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator targetElement = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(targetElement)) {
        targetElement.press("Enter");
      }
      return null;
    });
    return this;
  }

  @Override
  public SelectOption selectOption(Element locator) {
    return new SelectOption() {
      @Override
      public InteractionCapability byValue(String value) {
        session.runOnApiThread(() -> {
          com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
          if (ObjectUtils.isNotEmpty(el)) {
            el.selectOption(new com.microsoft.playwright.options.SelectOption().setValue(value));
          }
          return null;
        });
        return PlaywrightInteractionCapability.this;
      }

      @Override
      public InteractionCapability byIndex(int index) {
        session.runOnApiThread(() -> {
          com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
          if (ObjectUtils.isNotEmpty(el)) {
            el.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(index));
          }
          return null;
        });
        return PlaywrightInteractionCapability.this;
      }

      @Override
      public InteractionCapability byVisibleText(String visibleText) {
        session.runOnApiThread(() -> {
          com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
          if (ObjectUtils.isNotEmpty(el)) {
            el.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(visibleText));
          }
          return null;
        });
        return PlaywrightInteractionCapability.this;
      }
    };
  }

  @Override
  public com.microsoft.playwright.Locator findElement(Element locator) {
    try {
      return session.runOnApiThread(() -> resolveOnApiThreadOnly(locator));
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public List<com.microsoft.playwright.Locator> findElements(Element locator) {
    try {
      return session.runOnApiThread(() -> {
        List<com.microsoft.playwright.Locator> list = resolveElementsOnApiThreadOnly(locator);
        return list != null ? list : List.of();
      });
    } catch (Exception e) {
      return List.of();
    }
  }
}
