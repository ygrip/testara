package io.github.ygrip.testara.ui.playwright.capability;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;

import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightWaitCapability implements WaitCapability {
  private final Page page;
  private Duration defaultTimeout = Duration.ofSeconds(10);

  public PlaywrightWaitCapability(Page page) {
    this.page = page;
  }

  @Override
  public WaitCapability withTimeout(Duration duration) {
    this.defaultTimeout = duration;
    return this;
  }

  @Override
  public WaitPage untilPageLoaded(NamedPage namedPage) {
    return duration -> {
      PageContext<?> pageCtx = namedPage.getPage();
      if (ObjectUtils.isNotEmpty(pageCtx)) {
        pageCtx.isCurrentPage(duration);
      }
      namedPage.getFinder().setCurrentPage(pageCtx);
      return PlaywrightWaitCapability.this;
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
            return Optional.ofNullable(page.url())
              .filter(StringUtils::isNotBlank)
              .map(current -> current.contains(url))
              .orElse(false);
          } catch (Exception err) {
            return false;
          }
        });
      return PlaywrightWaitCapability.this;
    };
  }

  @Override
  public WaitCapability untilSelected(Element locator) {
    ElementHandle targetElement = element(locator);
    if (targetElement != null) {
      targetElement.waitForSelector("", new ElementHandle.WaitForSelectorOptions().setTimeout(defaultTimeout.toMillis()));
    }
    return this;
  }

  @Override
  public WaitCapability untilVisible(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        ElementHandle el = element(locator);
        return el != null && el.isVisible();
      });
    return this;
  }

  @Override
  public WaitCapability untilInvisible(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        ElementHandle el = element(locator);
        return el == null || !el.isVisible();
      });
    return this;
  }

  @Override
  public WaitCapability untilClickable(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        ElementHandle el = element(locator);
        return el != null && el.isVisible() && el.isEnabled();
      });
    return this;
  }

  @Override
  public WaitCapability untilPresent(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        try {
          return locator.one() != null;
        } catch (Exception e) {
          return false;
        }
      });
    return this;
  }

  @Override
  public WaitCapability untilEnabled(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        ElementHandle el = element(locator);
        return Optional.ofNullable(el)
          .map(ElementHandle::isEnabled)
          .orElse(false);
      });
    return this;
  }

  @Override
  public WaitCapability untilDisabled(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        ElementHandle el = element(locator);
        return !Optional.ofNullable(el)
          .map(ElementHandle::isEnabled)
          .orElse(false);
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
  private ElementHandle element(Element locator) {
    try {
      return (ElementHandle) locator.one(defaultTimeout);
    } catch (Exception e) {
      log.warn("Unable to find element on {}", locator.getLocator());
      return null;
    }
  }
}
