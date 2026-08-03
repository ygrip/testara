package io.github.ygrip.testara.ui.selenium.capability;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

import lombok.extern.log4j.Log4j2;

/**
 * Selenium implementation of {@link WaitCapability}. Uses WebDriverWait.
 */
@Log4j2
public final class SeleniumWaitCapability implements WaitCapability {
  private final WebDriver driver;
  private Duration defaultTimeout = Duration.ofSeconds(10);

  public SeleniumWaitCapability(WebDriver driver) {
    this.driver = driver;
  }

  @Override
  public WaitCapability withTimeout(Duration duration) {
    this.defaultTimeout = duration;
    return this;
  }

  @Override
  public WaitPage untilPageLoaded(NamedPage namedPage) {
    return duration -> {
      PageContext<?> page = namedPage.getPage();
      if (ObjectUtils.isNotEmpty(page) && page.isCurrentPage(duration)) {
        namedPage.getFinder().setCurrentPage(page);
      }
      return SeleniumWaitCapability.this;
    };
  }

  @Override
  public WaitPage untilUrlContains(String url) {
    return duration -> {
      Awaitility.await()
        .pollInSameThread()
        .atMost(duration.plusMillis(1))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> {
          try {
            return Optional.ofNullable(driver.getCurrentUrl())
              .filter(StringUtils::isNotBlank)
              .map(current -> current.contains(url))
              .orElse(false);
          } catch (Exception err) {
            return false;
          }
        });
      return SeleniumWaitCapability.this;
    };
  }

  @Override
  public WaitCapability untilSelected(Element locator) {
    WebElement targetElement = element(locator);
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until(ExpectedConditions.elementToBeSelected(targetElement));
    return this;
  }

  @Override
  public WaitCapability untilVisible(Element locator) {
    WebElement targetElement = element(locator);
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until(ExpectedConditions.visibilityOf(targetElement));
    return this;
  }

  @Override
  public WaitCapability untilInvisible(Element locator) {
    WebElement targetElement = element(locator);
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until(ExpectedConditions.invisibilityOf(targetElement));
    return this;
  }

  @Override
  public WaitCapability untilClickable(Element locator) {
    WebElement targetElement = element(locator);
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until(ExpectedConditions.elementToBeClickable(targetElement));
    return this;
  }

  @Override
  public WaitCapability untilPresent(Element locator) {
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until(driver -> {
        try {
          return locator.one();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    return this;
  }

  @Override
  public WaitCapability untilEnabled(Element locator) {
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until((ExpectedCondition<Boolean>) driver -> {
        try {
          WebElement target = element(locator);
          return Optional.ofNullable(target)
            .map(WebElement::isEnabled)
            .orElse(false);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    return this;
  }

  @Override
  public WaitCapability untilDisabled(Element locator) {
    new WebDriverWait(driver, defaultTimeout).withTimeout(defaultTimeout)
      .pollingEvery(Duration.ofMillis(100))
      .until((ExpectedCondition<Boolean>) driver -> {
        try {
          WebElement target = element(locator);
          return !Optional.ofNullable(target)
            .map(WebElement::isEnabled)
            .orElse(false);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    return this;
  }

  @Override
  public WaitCapability forDuration(Duration duration) {
    Awaitility.await()
      .pollInSameThread()
      .timeout(duration.plusMillis(1))
      .pollDelay(duration)
      .ignoreExceptions()
      .untilAsserted(() -> assertThat(true, equalTo(true)));
    return this;
  }

  @SuppressWarnings({"rawtypes"})
  private WebElement element(Element locator) {
    try {
      return (WebElement) locator.one(defaultTimeout);
    } catch (Exception e) {
      log.warn("Unable to find element on {}", locator.getLocator());
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<WebElement> elements(Element locator) {
    try {
      return (List<WebElement>) locator.all(defaultTimeout);
    } catch (Exception e) {
      log.warn("Unable to find multiple elements on {}", locator.getLocator());
      return null;
    }
  }
}
