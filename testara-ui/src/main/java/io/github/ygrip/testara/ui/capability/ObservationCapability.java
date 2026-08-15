package io.github.ygrip.testara.ui.capability;

import java.time.Duration;
import java.util.List;

import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.model.CapturedScreenshot;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.support.Screenshots;

/**
 * Fluent observation (get text, get value, etc).
 */
public interface ObservationCapability<E> {

  /** Execute javascript on the page. Returns value. */
  <T> T fromScript(String script, Object... args);

  /** Get visible text of the element. */
  String getText(Element locator);

  /** Get all text of the matching elements. */
  List<String> getTexts(Element locator);

  /** Get value of the element. */
  String getValue(Element locator);

  /** Get all value of the matching element. */
  List<String> getValues(Element locator);

  /** Get current url. */
  String getCurrentUrl();

  /** Get page title url. */
  String getPageTitle();

  /** Get browser cookie by name. */
  CapturedCookie getCookieNamed(String name);

  /** Get all browser cookies */
  List<CapturedCookie> getCookies();

  /** Get attribute value. */
  String getAttribute(Element locator, String attributeName);

  /** Get attribute value. */
  String getCssValue(Element locator, String attributeName);

  /** Resolve element (engine-specific handle, e.g. WebElement). Returns null if not found. */
  E findOne(Element locator);

  /** Resolve child element (engine-specific handle, e.g. WebElement). Returns null if not found. */
  E findOneChild(Element locator);

  /** Resolve all matching elements. Returns empty list if none. */
  List<E> findAll(Element locator);

  /** Resolve all matching child elements. Returns empty list if none. */
  List<E> findAllChild(Element locator);

  /** Start fluent page capture: {@code capturePage().fullPage()}. */
  ScreenshotCapture capturePage();

  /** Capture a specific element as PNG bytes. */
  byte[] captureElement(Element locator);

  /** Capture a specific region of the page as PNG bytes. */
  byte[] captureRegion(int x, int y, int width, int height);

  /** Fluent step for page-level screenshot capture. */
  interface ScreenshotCapture {
    /** Capture only the visible viewport as raw PNG bytes. */
    byte[] visibleOnViewPort();

    /**
     * Capture the visible viewport using the requested quality preset.
     * Engines can override this to use a native JPEG path; the default keeps
     * every engine compatible through the lightweight common optimizer.
     */
    default CapturedScreenshot visibleOnViewPort(ScreenshotQuality quality) {
      Screenshots.OptimizedScreenshot optimized = Screenshots.optimize(visibleOnViewPort(), quality);
      return new CapturedScreenshot(optimized.bytes(), optimized.mimeType());
    }

    /** Capture the full scrollable page. */
    byte[] fullPage();
  }
}
