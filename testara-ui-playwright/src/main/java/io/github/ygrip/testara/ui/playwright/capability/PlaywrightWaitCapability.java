package io.github.ygrip.testara.ui.playwright.capability;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;

import io.github.ygrip.testara.ui.capability.PageWaitSupport;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;

import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightWaitCapability extends PlaywrightElementResolver implements WaitCapability {
  private Duration defaultTimeout = Duration.ofSeconds(10);

  public PlaywrightWaitCapability(PlaywrightSession session) {
    super(session);
  }

  @Override
  public WaitCapability withTimeout(Duration duration) {
    this.defaultTimeout = duration;
    return this;
  }

  @Override
  public WaitPage untilPageLoaded(NamedPage namedPage) {
    return duration -> {
      PageWaitSupport.requireLoaded(namedPage, duration);
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
            return session.runOnApiThread(() -> Optional.ofNullable(session.pageForApi().url())
              .filter(StringUtils::isNotBlank)
              .map(current -> current.contains(url))
              .orElse(false));
          } catch (Exception err) {
            return false;
          }
        });
      return PlaywrightWaitCapability.this;
    };
  }

  @Override
  public WaitCapability untilSelected(Element locator) {
    Awaitility.await()
      .pollInSameThread()
      .atMost(defaultTimeout.plusMillis(1))
      .pollInterval(Duration.ofMillis(100))
      .ignoreExceptions()
      .until(() -> {
        try {
          return session.runOnApiThread(() -> {
            com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
            if (el == null) {
              return false;
            }
            Boolean selected = (Boolean) el.evaluate(
              "e => (e.tagName === 'OPTION' && e.selected) || e.checked === true"
            );
            return Boolean.TRUE.equals(selected);
          });
        } catch (Exception e) {
          return false;
        }
      });
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
        try {
          return session.runOnApiThread(() -> {
            com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
            return el != null && el.isVisible();
          });
        } catch (Exception e) {
          return false;
        }
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
        try {
          return session.runOnApiThread(() -> {
            com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
            return el == null || !el.isVisible();
          });
        } catch (Exception e) {
          return false;
        }
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
        try {
          return session.runOnApiThread(() -> {
            com.microsoft.playwright.Locator el = resolveOnApiThreadOnly(locator);
            return el != null && el.isVisible() && el.isEnabled();
          });
        } catch (Exception e) {
          return false;
        }
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
          return session.runOnApiThread(() -> {
            try {
              return locator.one() != null;
            } catch (Exception e) {
              return false;
            }
          });
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
        try {
          return session.runOnApiThread(() -> Optional.ofNullable(resolveOnApiThreadOnly(locator))
            .map(com.microsoft.playwright.Locator::isEnabled)
            .orElse(false));
        } catch (Exception e) {
          return false;
        }
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
        try {
          return session.runOnApiThread(() -> !Optional.ofNullable(resolveOnApiThreadOnly(locator))
            .map(com.microsoft.playwright.Locator::isEnabled)
            .orElse(false));
        } catch (Exception e) {
          return false;
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
}
