package io.github.ygrip.testara.ui.playwright.capability;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.page.Element;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightObservationCapability extends PlaywrightElementResolver
  implements ObservationCapability<ElementHandle> {
  private final Page page;

  public PlaywrightObservationCapability(Page page) {
    this.page = page;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T fromScript(String script, Object... args) {
    return (T) page.evaluate(script, args.length == 1 ? args[0] : args);
  }

  @Override
  public String getText(Element locator) {
    return text(locator);
  }

  @Override
  public List<String> getTexts(Element locator) {
    return findAll(locator).stream()
      .map(ElementHandle::textContent)
      .collect(Collectors.toList());
  }

  @Override
  public List<String> getValues(Element locator) {
    return findAll(locator).stream()
      .map(el -> el.getAttribute("value"))
      .collect(Collectors.toList());
  }

  @Override
  public String getValue(Element locator) {
    return value(locator);
  }

  @Override
  public String getCurrentUrl() {
    return page.url();
  }

  @Override
  public String getPageTitle() {
    return page.title();
  }

  @Override
  public String getAttribute(Element locator, String attributeName) {
    return attribute(locator, attributeName);
  }

  @Override
  public String getCssValue(Element locator, String attributeName) {
    ElementHandle el = findOne(locator);
    if (el == null) {
      return null;
    }
    return (String) el.evaluate(
      "(el, prop) => window.getComputedStyle(el).getPropertyValue(prop)",
      attributeName
    );
  }

  @Override
  public ElementHandle findOne(Element locator) {
    return element(locator);
  }

  @Override
  public ElementHandle findOneChild(Element locator) {
    return child(locator);
  }

  @Override
  public List<ElementHandle> findAll(Element locator) {
    return elements(locator);
  }

  @Override
  public List<ElementHandle> findAllChild(Element locator) {
    return children(locator);
  }

  @Override
  public ScreenshotCapture capturePage() {
    return new ScreenshotCapture() {
      @Override
      public byte[] visibleOnViewPort() {
        return page.screenshot();
      }

      @Override
      public byte[] fullPage() {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
      }
    };
  }

  @Override
  public byte[] captureElement(Element locator) {
    ElementHandle el = element(locator);
    if (ObjectUtils.isNotEmpty(el)) {
      return el.screenshot();
    }
    return new byte[0];
  }

  @Override
  public byte[] captureRegion(int x, int y, int width, int height) {
    return page.screenshot(new Page.ScreenshotOptions()
      .setClip(x, y, width, height));
  }
}
