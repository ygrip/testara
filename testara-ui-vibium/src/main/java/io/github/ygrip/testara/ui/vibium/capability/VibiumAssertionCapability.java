package io.github.ygrip.testara.ui.vibium.capability;

import java.util.Objects;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.vibium.errors.VibiumException;

import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;

import lombok.extern.log4j.Log4j2;

/**
 * Vibium's {@link AssertionCapability}.
 *
 * <p>Per this module's stricter error model (throw typed errors, never log-and-return-null), the
 * boolean query methods ({@link #isVisible}, {@link #isHidden}, {@link #isEnabled}, {@link
 * #isPresent}) return {@code false} when the state genuinely isn't met (including a locator that
 * does not resolve at all — that IS "not visible"/"not present") and throw only for a genuine
 * operational failure: a discovery-only (XPath/semantic) element rejected by {@link
 * VibiumElement#requireInteractionSafe} (it will never become interaction-safe by retrying), or a
 * real {@code com.vibium.errors.VibiumException} from the native check itself.
 *
 * <p>The throwing {@code seeThat*} methods throw a real exception carrying expected-vs-actual
 * detail rather than a bare {@link AssertionError}. {@code SeeThat#perform} (see {@code
 * io.github.ygrip.testara.ui.interaction.SeeThat}) already rethrows any {@code RuntimeException}
 * escaping here as an {@code AssertionError}, and passes an {@code AssertionError} through
 * unchanged, so both {@link AssertionError} (used directly for a mismatch, matching the Playwright
 * convention) and {@code RuntimeException} (used for resolution/native failures via {@link
 * io.github.ygrip.testara.ui.vibium.error.VibiumElementResolutionException}/{@link
 * VibiumOperationException}) surface correctly as a failed test.
 */
@Log4j2
public final class VibiumAssertionCapability extends VibiumElementResolver implements AssertionCapability {

  public VibiumAssertionCapability(VibiumSession session) {
    super(session);
  }

  @Override
  public AssertionCapability seeThatVisible(Element locator) {
    if (!isVisible(locator)) {
      throw new AssertionError("Element not visible: " + describeLocator(locator));
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatHidden(Element locator) {
    if (!isHidden(locator)) {
      throw new AssertionError("Element not hidden: " + describeLocator(locator));
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatAttribute(Element locator, String attributeName) {
    String actual = readAttribute(locator, attributeName, "seeThatAttribute");
    if (StringUtils.isBlank(actual)) {
      throw new AssertionError(
        "Element " + describeLocator(locator) + " does not have attribute '" + attributeName + "', actual: '"
          + actual + "'"
      );
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatText(Element locator, String expectedText) {
    String actual = readText(locator, "seeThatText");
    if (!Objects.equals(expectedText, actual)) {
      throw new AssertionError(
        "Expected text: '" + expectedText + "', actual: '" + actual + "' for " + describeLocator(locator)
      );
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatValue(Element locator, String value) {
    String actual = readValue(locator, "seeThatValue");
    if (!Objects.equals(value, actual)) {
      throw new AssertionError(
        "Expected value: '" + value + "', actual: '" + actual + "' for " + describeLocator(locator)
      );
    }
    return this;
  }

  @Override
  public AssertionCapability containsThatValue(Element locator, String value) {
    String actual = readValue(locator, "containsThatValue");
    if (actual == null || !actual.contains(value)) {
      throw new AssertionError(
        "Expected value to contain: '" + value + "', actual: '" + actual + "' for " + describeLocator(locator)
      );
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatContainsText(Element locator, String substring) {
    String actual = readText(locator, "seeThatContainsText");
    if (actual == null || !actual.contains(substring)) {
      throw new AssertionError(
        "Expected to contain: '" + substring + "', actual: '" + actual + "' for " + describeLocator(locator)
      );
    }
    return this;
  }

  @Override
  public AssertionCapability hasClass(Element locator, String className) {
    String actual = readAttribute(locator, "class", "hasClass");
    if (actual == null || !actual.contains(className)) {
      throw new AssertionError(
        "Expected to contain class: '" + className + "', actual: '" + actual + "' for " + describeLocator(locator)
      );
    }
    return this;
  }

  @Override
  public AssertionCapability seeThatPresent(Element locator) {
    if (!isPresent(locator)) {
      throw new AssertionError("Element not present: " + describeLocator(locator));
    }
    return this;
  }

  @Override
  public boolean isVisible(Element locator) {
    return checkNativeState(locator, "isVisible", el -> el.raw()
      .isVisible());
  }

  @Override
  public boolean isHidden(Element locator) {
    return checkNativeState(locator, "isHidden", el -> el.raw()
      .isHidden());
  }

  @Override
  public boolean isEnabled(Element locator) {
    return checkNativeState(locator, "isEnabled", el -> el.raw()
      .isEnabled());
  }

  @Override
  public boolean isPresent(Element locator) {
    // Resolution succeeding IS presence; no further native round-trip, and no
    // interaction-safety requirement either — a discovery-only element is still "present".
    return resolveElement(locator) != null;
  }

  @Override
  public AssertionCapability isOn(NamedPage namedPage) {
    var pageContext = namedPage.getPage();
    if (ObjectUtils.isNotEmpty(pageContext)) {
      if (!pageContext.isCurrentPage()) {
        throw new AssertionError("Page " + pageContext.metadata()
          .name() + " is not current page");
      }
      namedPage.getFinder()
        .setCurrentPage(pageContext);
    } else {
      throw new AssertionError("Page " + namedPage.getName() + " not found");
    }
    return this;
  }

  /**
   * Resolve, then evaluate a native boolean check. Returns {@code false} for a locator that does
   * not resolve at all (genuinely not visible/present). Throws {@link
   * io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException} for a
   * discovery-only element (it will never become interaction-safe), and {@link
   * VibiumOperationException} for a real native failure during the check itself — both are
   * genuine operational failures, not "state not met".
   */
  private boolean checkNativeState(Element locator, String operationName, Predicate<VibiumElement> nativeCheck) {
    VibiumElement el = resolveElement(locator);
    if (el == null) {
      return false;
    }
    el.requireInteractionSafe(operationName);
    try {
      return nativeCheck.test(el);
    } catch (VibiumException e) {
      throw VibiumOperationException.of(operationName, describeLocator(locator), safePageUrl(), 0L, e);
    }
  }

  private String readText(Element locator, String operationName) {
    VibiumElement el = requireElement(locator, operationName);
    el.requireInteractionSafe(operationName);
    try {
      return el.raw()
        .text();
    } catch (VibiumException e) {
      throw VibiumOperationException.of(operationName, describeLocator(locator), safePageUrl(), 0L, e);
    }
  }

  private String readValue(Element locator, String operationName) {
    VibiumElement el = requireElement(locator, operationName);
    el.requireInteractionSafe(operationName);
    try {
      return el.raw()
        .value();
    } catch (VibiumException e) {
      throw VibiumOperationException.of(operationName, describeLocator(locator), safePageUrl(), 0L, e);
    }
  }

  private String readAttribute(Element locator, String attributeName, String operationName) {
    VibiumElement el = requireElement(locator, operationName);
    el.requireInteractionSafe(operationName);
    try {
      return el.raw()
        .getAttribute(attributeName);
    } catch (VibiumException e) {
      throw VibiumOperationException.of(operationName, describeLocator(locator), safePageUrl(), 0L, e);
    }
  }
}
