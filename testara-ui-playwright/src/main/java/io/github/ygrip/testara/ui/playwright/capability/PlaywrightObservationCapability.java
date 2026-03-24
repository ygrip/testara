package io.github.ygrip.testara.ui.playwright.capability;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.page.Element;
import com.microsoft.playwright.Page;

import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightObservationCapability extends PlaywrightElementResolver
    implements ObservationCapability<com.microsoft.playwright.Locator> {

  public PlaywrightObservationCapability(PlaywrightSession session) {
    super(session);
  }

  private static String wrapAsSeleniumScript(String script) {
    String body = Optional.ofNullable(script)
        .orElse("")
        .replaceAll("\\barguments\\s*\\[", "seleniumArgs[");
    return "function(arg) {"
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
  public <T> T fromScript(String script, Object... args) {
    String wrappedScript = wrapAsSeleniumScript(script);
    return session.runOnApiThread(() -> {
      Object[] resolved = resolveEvalArgs(args);
      return (T) session.pageForApi()
        .evaluate(wrappedScript, resolved.length == 1 ? resolved[0] : resolved);
    });
  }

  @Override
  public String getText(Element locator) {
    return text(locator);
  }

  @Override
  public List<String> getTexts(Element locator) {
    return session.runOnApiThread(() -> {
      List<com.microsoft.playwright.Locator> resolved = resolveElementsOnApiThreadOnly(locator);
      if (resolved == null || resolved.isEmpty()) {
        return List.<String>of();
      }
      return resolved.stream()
          .map(el -> Optional.ofNullable(el.textContent()).orElse(""))
          .collect(Collectors.toList());
    });
  }

  @Override
  public List<String> getValues(Element locator) {
    return session.runOnApiThread(() -> {
      List<com.microsoft.playwright.Locator> resolved = resolveElementsOnApiThreadOnly(locator);
      if (resolved == null || resolved.isEmpty()) {
        return List.of();
      }
      return resolved.stream()
          .map(el -> {
            Object value = el.evaluate("e => String(e.value ?? e.getAttribute('value') ?? '')");
            return value != null ? value.toString() : "";
          })
          .collect(Collectors.toList());
    });
  }

  @Override
  public String getValue(Element locator) {
    return value(locator);
  }

  @Override
  public String getCurrentUrl() {
    return session.runOnApiThread(() -> session.pageForApi().url());
  }

  @Override
  public String getPageTitle() {
    return session.runOnApiThread(() -> session.pageForApi().title());
  }

  @Override
  public String getAttribute(Element locator, String attributeName) {
    return attribute(locator, attributeName);
  }

  @Override
  public String getCssValue(Element locator, String attributeName) {
    return session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
      if (el == null) {
        return null;
      }
      return (String) el.evaluate(
          "(el, prop) => window.getComputedStyle(el).getPropertyValue(prop)",
          attributeName);
    });
  }

  @Override
  public com.microsoft.playwright.Locator findOne(Element locator) {
    try {
      return session.runOnApiThread(() -> resolveOnApiThreadOnly(locator));
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public com.microsoft.playwright.Locator findOneChild(Element locator) {
    try {
      return session.runOnApiThread(() -> resolveChildOnApiThreadOnly(locator));
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public List<com.microsoft.playwright.Locator> findAll(Element locator) {
    try {
      return session.runOnApiThread(() -> {
        List<com.microsoft.playwright.Locator> list = resolveElementsOnApiThreadOnly(locator);
        return list != null ? list : List.of();
      });
    } catch (Exception e) {
      return List.of();
    }
  }

  @Override
  public List<com.microsoft.playwright.Locator> findAllChild(Element locator) {
    try {
      return session.runOnApiThread(() -> {
        List<com.microsoft.playwright.Locator> list = resolveChildrenOnApiThreadOnly(locator);
        return list != null ? list : List.of();
      });
    } catch (Exception e) {
      return List.of();
    }
  }

  @Override
  public ScreenshotCapture capturePage() {
    return new ScreenshotCapture() {
      @Override
      public byte[] visibleOnViewPort() {
        return session.runOnApiThread(() -> {
          Page p = session.pageForApi();
          return p.screenshot();
        });
      }

      @Override
      public byte[] fullPage() {
        return session.runOnApiThread(() -> {
          Page p = session.pageForApi();
          return p.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        });
      }
    };
  }

  @Override
  public byte[] captureElement(Element locator) {
    return session.runOnApiThread(() -> {
      com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
      if (ObjectUtils.isNotEmpty(el)) {
        return el.screenshot();
      }
      return new byte[0];
    });
  }

  @Override
  public byte[] captureRegion(int x, int y, int width, int height) {
    return session.runOnApiThread(() -> session.pageForApi().screenshot(new Page.ScreenshotOptions()
        .setClip(x, y, width, height)));
  }
}
