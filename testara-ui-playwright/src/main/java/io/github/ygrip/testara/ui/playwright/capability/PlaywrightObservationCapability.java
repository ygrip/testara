package io.github.ygrip.testara.ui.playwright.capability;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.model.CapturedScreenshot;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.support.Screenshots;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.ScreenshotType;

import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightObservationCapability extends PlaywrightElementResolver
    implements ObservationCapability<com.microsoft.playwright.Locator> {
  private static final String JPEG_MIME_TYPE = "image/jpeg";

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

  /** Convert any Playwright Locator args to ElementHandle so they can be passed to page.evaluate(). */
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
        return session.runOnApiThread(() -> session.pageForApi().screenshot());
      }

      @Override
      public CapturedScreenshot visibleOnViewPort(ScreenshotQuality quality) {
        ScreenshotQuality selected = quality == null ? ScreenshotQuality.STANDARD : quality;
        byte[] jpeg = session.runOnApiThread(() -> session.pageForApi().screenshot(
          new Page.ScreenshotOptions()
            .setType(ScreenshotType.JPEG)
            .setQuality(Math.round(selected.jpegQuality() * 100))
            .setScale(ScreenshotScale.CSS)
        ));
        Screenshots.OptimizedScreenshot optimized = Screenshots.optimize(jpeg, JPEG_MIME_TYPE, selected);
        return new CapturedScreenshot(optimized.bytes(), optimized.mimeType());
      }

      @Override
      public byte[] fullPage() {
        return session.runOnApiThread(() -> session.pageForApi().screenshot(
          new Page.ScreenshotOptions().setFullPage(true)));
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

  @Override
  public CapturedCookie getCookieNamed(String name) {
    final var cookie = await().alias("%s cookie to be present".formatted(name))
      .atMost(Duration.ofSeconds(10))
      .pollInterval(Duration.ofMillis(100))
      .until(
        () -> Objects.requireNonNull(session.contextForApi()
          .cookies()
          .stream()
          .filter(capture -> capture.name.equals(name))
          .findAny()
          .orElse(null)), obj -> true
      );

    return toCapturedCookie(cookie);
  }

  private CapturedCookie toCapturedCookie(Cookie cookie) {
    return Optional.ofNullable(cookie)
      .map(captured -> CapturedCookie.builder()
        .name(captured.name)
        .path(captured.path)
        .maxAge(Optional.ofNullable(captured.expires)
          .orElse(0D)
          .longValue())
        .value(captured.value)
        .secured(captured.secure)
        .domain(captured.domain)
        .httpOnly(captured.httpOnly)
        .sameSite(captured.sameSite.name())
        .expiryDate(new Date(cookie.expires.longValue() * 1000))
        .build())
      .orElse(null);
  }

  @Override
  public List<CapturedCookie> getCookies() {
    final var cookies = await().alias("wait cookie to be present")
      .atMost(Duration.ofSeconds(10))
      .pollInterval(Duration.ofMillis(100))
      .until(
        () -> session.contextForApi()
          .cookies(), obj -> true
      );

    return cookies.stream()
      .map(this::toCapturedCookie)
      .collect(Collectors.toList());
  }
}
