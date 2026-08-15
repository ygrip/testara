package io.github.ygrip.testara.ui.selenium.capability;

import static org.awaitility.Awaitility.await;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ObjectUtils;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.bidi.HasBiDi;
import org.openqa.selenium.bidi.browsingcontext.BrowsingContext;
import org.openqa.selenium.bidi.browsingcontext.CaptureScreenshotParameters;
import org.openqa.selenium.chromium.HasCdp;

import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.model.CapturedScreenshot;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;
import io.github.ygrip.testara.ui.page.Element;

import lombok.extern.log4j.Log4j2;

/**
 * Selenium implementation of {@link ObservationCapability}. Fluent, fail-fast.
 */
@Log4j2
public final class SeleniumObservationCapability extends SeleniumElementResolver
  implements ObservationCapability<WebElement> {
  private static final String JPEG_MIME_TYPE = "image/jpeg";
  private static final String PNG_MIME_TYPE = "image/png";
  private final WebDriver driver;

  public SeleniumObservationCapability(WebDriver driver) {
    this.driver = driver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T fromScript(String script, Object... args) {
    JavascriptExecutor js = (JavascriptExecutor) driver;
    return (T) js.executeScript(script, args);
  }

  @Override
  public String getText(Element locator) {
    return text(locator);
  }

  @Override
  public List<String> getTexts(Element locator) {
    return findAll(locator).stream()
      .map(WebElement::getText)
      .collect(Collectors.toList());
  }

  @Override
  public List<String> getValues(Element locator) {
    return findAll(locator).stream()
      .map(val -> val.getDomAttribute("value"))
      .collect(Collectors.toList());
  }

  @Override
  public String getValue(Element locator) {
    return value(locator);
  }

  @Override
  public String getCurrentUrl() {
    return driver.getCurrentUrl();
  }

  @Override
  public String getPageTitle() {
    return driver.getTitle();
  }

  @Override
  public CapturedCookie getCookieNamed(String name) {
    final var cookie = await().alias("%s cookie to be present".formatted(name))
      .atMost(Duration.ofSeconds(10))
      .pollInterval(Duration.ofMillis(100))
      .until(
        () -> driver.manage()
          .getCookieNamed(name), Objects::nonNull
      );

    return toCapturedCookie(cookie);
  }

  private CapturedCookie toCapturedCookie(Cookie cookie){
    return Optional.ofNullable(cookie)
      .map(captured -> CapturedCookie.builder()
        .name(captured.getName())
        .path(captured.getPath())
        .maxAge(Optional.ofNullable(captured.getExpiry())
          .map(Date::getTime)
          .orElse(0L))
        .value(captured.getValue())
        .secured(captured.isSecure())
        .domain(captured.getDomain())
        .httpOnly(captured.isHttpOnly())
        .sameSite(captured.getSameSite())
        .expiryDate(captured.getExpiry())
        .build())
      .orElse(null);
  }

  @Override
  public List<CapturedCookie> getCookies() {
    final var cookies = await().alias("wait cookie to be present")
      .atMost(Duration.ofSeconds(10))
      .pollInterval(Duration.ofMillis(100))
      .until(
        () -> driver.manage()
          .getCookies(), obj -> true
      );

    return cookies.stream()
      .map(this::toCapturedCookie)
      .collect(Collectors.toList());
  }

  @Override
  public String getAttribute(Element locator, String attributeName) {
    return attribute(locator, attributeName);
  }

  @Override
  public String getCssValue(Element locator, String attributeName) {
    return findOne(locator).getCssValue(attributeName);
  }

  @Override
  public WebElement findOne(Element locator) {
    return element(locator);
  }

  @Override
  public WebElement findOneChild(Element locator) {
    return child(locator);
  }

  @Override
  public List<WebElement> findAll(Element locator) {
    return elements(locator);
  }

  @Override
  public List<WebElement> findAllChild(Element locator) {
    return children(locator);
  }

  @Override
  public ScreenshotCapture capturePage() {
    return new ScreenshotCapture() {
      @Override
      public byte[] visibleOnViewPort() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
      }

      @Override
      public CapturedScreenshot fastVisibleOnViewPort(ScreenshotQuality quality) {
        ScreenshotQuality selected = quality == null ? ScreenshotQuality.STANDARD : quality;
        if (driver instanceof HasBiDi hasBiDi && hasBiDi.maybeGetBiDi().isPresent()) {
          try {
            String encoded = new BrowsingContext(driver, driver.getWindowHandle())
              .captureScreenshot(new CaptureScreenshotParameters()
                .imageFormat(JPEG_MIME_TYPE, selected.jpegQuality()));
            return new CapturedScreenshot(Base64.getDecoder().decode(encoded), JPEG_MIME_TYPE);
          } catch (Exception e) {
            log.debug("BiDi screenshot capture unavailable, trying CDP/WebDriver fallback: {}", e.getMessage());
          }
        }

        if (driver instanceof HasCdp hasCdp) {
          try {
            Map<String, Object> result = hasCdp.executeCdpCommand(
              "Page.captureScreenshot",
              Map.of(
                "format", "jpeg",
                "quality", Math.round(selected.jpegQuality() * 100),
                "captureBeyondViewport", false,
                "optimizeForSpeed", true
              )
            );
            Object data = result.get("data");
            if (data instanceof String encoded && !encoded.isBlank()) {
              return new CapturedScreenshot(Base64.getDecoder().decode(encoded), JPEG_MIME_TYPE);
            }
          } catch (Exception e) {
            log.debug("CDP screenshot capture unavailable, using WebDriver PNG fallback: {}", e.getMessage());
          }
        }

        return new CapturedScreenshot(visibleOnViewPort(), PNG_MIME_TYPE);
      }

      @Override
      public byte[] fullPage() {
        try {
          JavascriptExecutor js = (JavascriptExecutor) driver;
          var originalSize = driver.manage().window().getSize();
          long pageWidth = (long) js.executeScript("return document.body.scrollWidth");
          long pageHeight = (long) js.executeScript("return document.body.scrollHeight");
          driver.manage().window().setSize(
            new org.openqa.selenium.Dimension((int) pageWidth, (int) pageHeight));
          byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
          driver.manage().window().setSize(originalSize);
          return screenshot;
        } catch (Exception e) {
          log.warn("Full-page capture failed, falling back to viewport: {}", e.getMessage());
          return visibleOnViewPort();
        }
      }
    };
  }

  @Override
  public byte[] captureElement(Element locator) {
    WebElement el = element(locator);
    if (ObjectUtils.isNotEmpty(el)) {
      return el.getScreenshotAs(OutputType.BYTES);
    }
    return new byte[0];
  }

  @Override
  public byte[] captureRegion(int x, int y, int width, int height) {
    try {
      byte[] full = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(full));
      int cropX = Math.max(0, Math.min(x, img.getWidth()));
      int cropY = Math.max(0, Math.min(y, img.getHeight()));
      int cropW = Math.min(width, img.getWidth() - cropX);
      int cropH = Math.min(height, img.getHeight() - cropY);
      BufferedImage cropped = img.getSubimage(cropX, cropY, cropW, cropH);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(cropped, "png", out);
      return out.toByteArray();
    } catch (Exception e) {
      log.warn("Failed to capture region screenshot: {}", e.getMessage());
      return new byte[0];
    }
  }
}