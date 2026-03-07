package io.github.ygrip.testara.ui.capability;

import java.time.Duration;

import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

/**
 * Fluent wait (until visible, clickable, etc.). Chainable with optional timeout.
 */
public interface WaitCapability {

  /** Set default timeout for subsequent until* calls. Returns this. */
  WaitCapability withTimeout(Duration duration);

  WaitPage untilPageLoaded(NamedPage namedPage);

  WaitPage untilUrlContains(String url);

  /** Wait until element is selected. */
  WaitCapability untilSelected(Element locator);

  /** Wait until element is visible. */
  WaitCapability untilVisible(Element locator);

  /** Wait until element is not visible. */
  WaitCapability untilInvisible(Element locator);

  /** Wait until element is clickable. */
  WaitCapability untilClickable(Element locator);

  /** Wait until element is present in DOM. */
  WaitCapability untilPresent(Element locator);

  /** Wait until element is enabled in DOM. */
  WaitCapability untilEnabled(Element locator);

  /** Wait until element is disabled in DOM. */
  WaitCapability untilDisabled(Element locator);

  /** Wait for fixed time (use sparingly). */
  WaitCapability forDuration(Duration duration);

  /** Fluent step for entering text into a locator. */
  interface WaitPage {
    WaitCapability forDuration(Duration duration);
  }
}
