package io.github.ygrip.testara.ui.vibium.capability;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import com.vibium.errors.VibiumException;
import com.vibium.types.FindOptions;

import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;

import lombok.extern.log4j.Log4j2;

/**
 * Vibium's {@link WaitCapability}.
 *
 * <p><b>Native support, confirmed empirically against a real running Vibium 26.5.31 server</b>
 * (see this module's Phase 3 Part A investigation): {@code com.vibium.Element#waitUntil(String,
 * FindOptions)} recognizes exactly four state strings — {@code visible}, {@code hidden}, {@code
 * attached}, {@code detached} — server-side; any other string (e.g. {@code enabled}, {@code
 * disabled}, {@code present}, {@code clickable}, {@code selected}) is rejected immediately with a
 * native {@code VibiumException: unknown state: X (expected visible, hidden, attached,
 * detached)}, not a timeout. Therefore:
 * <ul>
 *   <li>{@link #untilVisible}/{@link #untilInvisible} delegate to the native {@code
 *       waitUntil("visible"|"hidden", ...)}.
 *   <li>{@link #untilPresent} does not need a native round-trip at all: a {@link VibiumElement}
 *       can only be constructed for a selector that already matched, so successful resolution
 *       already establishes presence/attachment; this polls resolution only.
 *   <li>{@link #untilEnabled}/{@link #untilDisabled}/{@link #untilClickable}/{@link
 *       #untilSelected} have no native equivalent and fall back to Awaitility-based manual
 *       polling of {@code Element#isEnabled()}/{@code isVisible()}/{@code isChecked()} (each of
 *       which is itself a real native round-trip — see {@link VibiumElement#requireInteractionSafe}).
 *       Vibium has no generic "selected" state or per-element JS-evaluate hook, so {@code
 *       untilSelected} approximates Playwright's checkbox-or-option-selected check using {@code
 *       isChecked()} OR presence of a {@code selected} attribute.
 * </ul>
 *
 * <p>{@link #untilUrlContains} does not use the native {@code Page#waitForURL(String,
 * WaitOptions)}: that primitive takes an opaque {@code pattern} string matched entirely
 * server-side, and this client jar has no way to confirm whether it performs exact, glob, or
 * substring matching without server source — risking silently-wrong "contains" semantics wasn't
 * worth it, so this polls {@code page.url()} instead (same approach Playwright uses).
 */
@Log4j2
public final class VibiumWaitCapability extends VibiumElementResolver implements WaitCapability {
  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  private Duration defaultTimeout = Duration.ofSeconds(10);

  public VibiumWaitCapability(VibiumSession session) {
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
      PageContext<?> pageCtx = namedPage.getPage();
      if (ObjectUtils.isNotEmpty(pageCtx) && pageCtx.isCurrentPage(duration)) {
        namedPage.getFinder()
          .setCurrentPage(pageCtx);
      }
      return VibiumWaitCapability.this;
    };
  }

  @Override
  public WaitPage untilUrlContains(String url) {
    return duration -> {
      try {
        Awaitility.await()
          .pollInSameThread()
          .atMost(duration.plusMillis(1))
          .pollInterval(POLL_INTERVAL)
          .until(() -> Optional.ofNullable(safeCurrentUrl())
            .filter(StringUtils::isNotBlank)
            .map(current -> current.contains(url))
            .orElse(false));
      } catch (ConditionTimeoutException e) {
        throw VibiumOperationException.of(
          "untilUrlContains", "url contains '" + url + "'", safePageUrl(), duration.toMillis(), e
        );
      }
      return VibiumWaitCapability.this;
    };
  }

  @Override
  public WaitCapability untilSelected(Element locator) {
    return pollPredicate(locator, "untilSelected", el -> {
      boolean checked = el.raw()
        .isChecked();
      if (checked) {
        return true;
      }
      return el.raw()
        .getAttribute("selected") != null;
    });
  }

  @Override
  public WaitCapability untilVisible(Element locator) {
    return waitNativeState(locator, "visible", "untilVisible");
  }

  @Override
  public WaitCapability untilInvisible(Element locator) {
    return waitNativeState(locator, "hidden", "untilInvisible");
  }

  @Override
  public WaitCapability untilClickable(Element locator) {
    return pollPredicate(locator, "untilClickable", el -> el.raw()
      .isVisible() && el.raw()
      .isEnabled());
  }

  @Override
  public WaitCapability untilPresent(Element locator) {
    pollResolve(locator, defaultTimeout, "untilPresent");
    return this;
  }

  @Override
  public WaitCapability untilEnabled(Element locator) {
    return pollPredicate(locator, "untilEnabled", el -> el.raw()
      .isEnabled());
  }

  @Override
  public WaitCapability untilDisabled(Element locator) {
    return pollPredicate(locator, "untilDisabled", el -> !el.raw()
      .isEnabled());
  }

  @Override
  public WaitCapability forDuration(Duration duration) {
    Awaitility.await()
      .pollInSameThread()
      .timeout(duration.plusMillis(1))
      .pollDelay(duration)
      .until(() -> true);
    return this;
  }

  /**
   * Resolve, then delegate to the confirmed-native {@code Element#waitUntil(state, FindOptions)}.
   * If the locator does not resolve yet, polls resolution first (a {@link VibiumElement} handle
   * can only exist for a selector that has already matched at least once).
   */
  private WaitCapability waitNativeState(Element locator, String state, String operationName) {
    VibiumElement resolved = pollResolve(locator, defaultTimeout, operationName);
    resolved.requireInteractionSafe(operationName);
    try {
      resolved.raw()
        .waitUntil(state, new FindOptions().timeout(defaultTimeout.toMillis()));
    } catch (VibiumException e) {
      throw VibiumOperationException.of(
        operationName, describeLocator(locator), safePageUrl(), defaultTimeout.toMillis(), e
      );
    }
    return this;
  }

  /**
   * Poll for a boolean state on a resolved element. {@link VibiumElement#requireInteractionSafe}
   * is deliberately NOT swallowed here: a discovery-only element will never become interaction-safe
   * by retrying, so that failure must escape the loop immediately rather than be retried into a
   * confusing timeout. A transient native read failure ({@link VibiumException} from the actual
   * state check) is treated as "not yet" and retried.
   */
  private WaitCapability pollPredicate(Element locator, String operationName, Predicate<VibiumElement> predicate) {
    try {
      Awaitility.await()
        .pollInSameThread()
        .atMost(defaultTimeout.plusMillis(1))
        .pollInterval(POLL_INTERVAL)
        .until(() -> {
          VibiumElement el = resolveElement(locator);
          if (el == null) {
            return false;
          }
          el.requireInteractionSafe(operationName);
          try {
            return predicate.test(el);
          } catch (VibiumException nativeError) {
            return false;
          }
        });
    } catch (ConditionTimeoutException e) {
      throw VibiumOperationException.of(
        operationName, describeLocator(locator), safePageUrl(), defaultTimeout.toMillis(), e
      );
    }
    return this;
  }

  /** Poll for the element to become resolvable at all. Never returns {@code null}. */
  private VibiumElement pollResolve(Element locator, Duration timeout, String operationName) {
    VibiumElement immediate = resolveElement(locator);
    if (immediate != null) {
      return immediate;
    }
    AtomicReference<VibiumElement> found = new AtomicReference<>();
    try {
      Awaitility.await()
        .pollInSameThread()
        .atMost(timeout.plusMillis(1))
        .pollInterval(POLL_INTERVAL)
        .until(() -> {
          VibiumElement candidate = resolveElement(locator);
          found.set(candidate);
          return candidate != null;
        });
    } catch (ConditionTimeoutException e) {
      throw VibiumOperationException.of(
        operationName, describeLocator(locator), safePageUrl(), timeout.toMillis(), e
      );
    }
    return found.get();
  }

  private String safeCurrentUrl() {
    try {
      return session.pageForApi()
        .url();
    } catch (Exception e) {
      return null;
    }
  }
}
