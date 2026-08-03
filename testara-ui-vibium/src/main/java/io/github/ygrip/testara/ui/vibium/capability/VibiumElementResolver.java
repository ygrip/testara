package io.github.ygrip.testara.ui.vibium.capability;

import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.VibiumElementResolutionException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;

import lombok.extern.log4j.Log4j2;

/**
 * Shared element-resolution base for Vibium capability adapters. Mirrors
 * {@code PlaywrightElementResolver}'s role, adapted to Vibium's stricter "throw typed errors,
 * never log-and-return-null for an operation" design: {@link #resolveElement} still returns
 * {@code null} on a plain zero-match (matching the null/empty-list "not found" contract already
 * used throughout {@link io.github.ygrip.testara.ui.vibium.page.VibiumPageFinder} — see e.g.
 * {@code getElementWithRoot}/{@code getChildNode}), but any capability method that needs a real
 * target to proceed must go through {@link #requireElement} instead of silently no-op-ing.
 *
 * <p>No API-thread wrapping is needed here, unlike {@code PlaywrightElementResolver}: Vibium's
 * Java client is not tied to a dedicated worker thread the way Playwright's is (Phase 1 decision;
 * see {@link io.github.ygrip.testara.ui.vibium.page.VibiumPage}'s javadoc for why), so every
 * method below calls straight through.
 */
@Log4j2
public abstract class VibiumElementResolver {
  protected final VibiumSession session;

  protected VibiumElementResolver(VibiumSession session) {
    this.session = session;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Element bindToSessionFinder(Element locator) {
    if (locator == null) {
      return null;
    }
    Element cursor = locator;
    while (cursor != null) {
      cursor.using(session.finder());
      cursor = cursor.child();
    }
    return locator;
  }

  /**
   * Resolve a Testara {@link Element}/{@link io.github.ygrip.testara.ui.model.Locator} down to a
   * {@link VibiumElement} via the session's {@link io.github.ygrip.testara.ui.vibium.page.VibiumPageFinder}.
   * Returns {@code null} on zero-match or any resolution failure — this is the expected "not
   * found" signal (e.g. a query/predicate capability method like
   * {@code AssertionCapability#isVisible} legitimately answers {@code false} for a missing
   * element rather than treating it as an operational error).
   */
  @SuppressWarnings({"rawtypes"})
  protected VibiumElement resolveElement(Element locator) {
    if (locator == null) {
      return null;
    }
    try {
      Element current = bindToSessionFinder(locator);
      while (current.child() != null) {
        current = current.child();
      }
      return (VibiumElement) current.one();
    } catch (Exception e) {
      log.debug("Unable to resolve element on {}: {}", describeLocator(locator), e.getMessage());
      return null;
    }
  }

  /**
   * Resolve an element, throwing {@link VibiumElementResolutionException} (with locator and page
   * context) if resolution fails. Use this from any capability method that must have a real
   * target to proceed (e.g. reading text/value/attribute, or a throwing {@code seeThat*}
   * assertion) rather than silently no-op-ing on a missing element.
   */
  @SuppressWarnings({"rawtypes"})
  protected VibiumElement requireElement(Element locator, String operationName) {
    VibiumElement resolved = resolveElement(locator);
    if (resolved == null) {
      throw new VibiumElementResolutionException(
        "Vibium could not resolve element for operation '" + operationName + "': locator="
          + describeLocator(locator) + ", page=" + safePageUrl()
      );
    }
    return resolved;
  }

  /** Human-readable locator description for error messages. Never throws. */
  @SuppressWarnings({"rawtypes"})
  protected String describeLocator(Element locator) {
    if (locator == null) {
      return "null";
    }
    try {
      return String.valueOf(locator.getLocator());
    } catch (Exception e) {
      return "<unavailable>";
    }
  }

  /**
   * Best-effort current page URL for error messages. Swallows a URL-read failure rather than
   * letting it mask the original error being reported.
   */
  protected String safePageUrl() {
    try {
      return session.pageForApi().url();
    } catch (Exception e) {
      return "<unavailable>";
    }
  }
}
