package io.github.ygrip.testara.ui.vibium.locator;

import com.vibium.Element;

import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;

/**
 * Wraps a resolved {@link com.vibium.Element} together with whether it is safe to interact with.
 *
 * <p>Vibium 26.5.31's Java client has a real bug: {@code Page.find(SelectorOptions)} (used for
 * role/text/label/placeholder/testid/alt/title semantic locators, and for xpath, since Vibium only
 * exposes xpath via {@code SelectorOptions.xpath(...)}) constructs the returned {@code Element}
 * with an empty selector string ({@code elementFromResult(result, "", 0)}). Every follow-up call
 * on that specific {@code Element} (click/text/attr/fill/etc, via {@code Element.elementParams()}
 * which resends the stored {@code selector} field) then throws a native
 * {@code com.vibium.errors.ElementNotFoundException}. Only elements resolved through a plain
 * CSS-string overload ({@code Page.find(String)}/{@code Element.find(String)}) carry a reusable
 * selector and are safe for follow-up interaction.
 *
 * <p>This wrapper carries an {@code interactionSafe} flag so a future Phase 3 capability adapter
 * can refuse to interact through an unsafe element with a clear
 * {@link UnsupportedVibiumCapabilityException} instead of a confusing native failure. Phase 2
 * itself does not call any mutating operation — it only builds and flags these wrappers.
 */
public final class VibiumElement {

  private final Element element;
  private final boolean interactionSafe;
  private final String resolvedVia;

  public VibiumElement(Element element, boolean interactionSafe, String resolvedVia) {
    this.element = element;
    this.interactionSafe = interactionSafe;
    this.resolvedVia = resolvedVia;
  }

  /** The raw Vibium element. For engine/capability internals only. */
  public Element raw() {
    return element;
  }

  public boolean isInteractionSafe() {
    return interactionSafe;
  }

  /** Human-readable description of the resolution strategy, for error messages. */
  public String resolvedVia() {
    return resolvedVia;
  }

  /**
   * Throws {@link UnsupportedVibiumCapabilityException} if this element was resolved through a
   * discovery-only strategy (semantic role/text/label/placeholder/testid/alt/title, or xpath, or
   * link-text). Phase 3 capability adapters must call this before every mutating or follow-up
   * operation (click, fill, text, attr, etc).
   *
   * @param operationName the operation about to be attempted, for the error message.
   */
  public void requireInteractionSafe(String operationName) {
    if (!interactionSafe) {
      throw new UnsupportedVibiumCapabilityException(
        operationName,
        "element was resolved via '" + resolvedVia + "', which Vibium 26.5.31 returns with no "
          + "reusable selector (client bug in com.vibium.Page#find(SelectorOptions)); only "
          + "CSS-derived locators (ID/CSS/CLASS/TAG/NAME) support follow-up interaction after "
          + "resolution. Use a CSS-based Locator instead, or perform a read-only operation "
          + "immediately after resolving this element."
      );
    }
  }

  @Override
  public String toString() {
    return "VibiumElement{resolvedVia='" + resolvedVia + "', interactionSafe=" + interactionSafe + "}";
  }
}
