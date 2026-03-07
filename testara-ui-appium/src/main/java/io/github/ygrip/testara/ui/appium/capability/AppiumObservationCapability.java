package io.github.ygrip.testara.ui.appium.capability;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ObjectUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;

import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.page.Element;

import io.appium.java_client.AppiumDriver;
import lombok.extern.log4j.Log4j2;

/**
 * Appium implementation of {@link ObservationCapability}. Fluent, fail-fast.
 */
@Log4j2
public final class AppiumObservationCapability extends AppiumElementResolver
  implements ObservationCapability<WebElement> {
  private final AppiumDriver driver;

  public AppiumObservationCapability(AppiumDriver driver) {
    this.driver = driver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T fromScript(String script, Object... args) {
    JavascriptExecutor js = driver;
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
      public byte[] fullPage() {
        try {
          JavascriptExecutor js = driver;
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
