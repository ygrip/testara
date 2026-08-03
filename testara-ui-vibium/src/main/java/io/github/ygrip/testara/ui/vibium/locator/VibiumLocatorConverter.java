package io.github.ygrip.testara.ui.vibium.locator;

import com.vibium.types.SelectorOptions;

import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Selector;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;

/**
 * Converts an engine-agnostic {@link Locator} into a Vibium-specific {@link VibiumSelector}.
 *
 * <p>Only CSS-derived strategies (ID/CSS/CLASS/TAG/NAME) produce an interaction-safe selector.
 * XPATH and LINKTEXT/PARTIALLINK resolve through Vibium's {@code SelectorOptions} semantic path,
 * which is discovery-only — Vibium only exposes xpath via {@code SelectorOptions.xpath(...)} (no
 * plain-string xpath path), and every {@code SelectorOptions}-based find hits the same
 * empty-selector client bug documented on {@link VibiumElement}. ACCESSIBILITY/
 * ANDROID_UI_AUTOMATOR/IOS_CLASS_CHAIN are Appium/mobile-only strategies with no browser-engine
 * equivalent and are rejected outright.
 */
public final class VibiumLocatorConverter {

  private VibiumLocatorConverter() {
  }

  public static VibiumSelector toSelector(Locator locator) {
    if (locator == null) {
      throw new IllegalArgumentException("locator cannot be null");
    }
    Selector strategy = locator.getStrategy();
    String value = locator.resolvedValue();
    return switch (strategy) {
      case ID -> VibiumSelector.css("#" + CssEscaping.escapeIdentifier(value));
      case CSS -> VibiumSelector.css(value);
      case CLASS -> VibiumSelector.css("." + CssEscaping.escapeIdentifier(value));
      case TAG -> VibiumSelector.css(CssEscaping.validateTagName(value));
      case NAME -> VibiumSelector.css("[name=\"" + CssEscaping.escapeAttributeValue(value) + "\"]");
      case XPATH -> VibiumSelector.discoveryOnly(new SelectorOptions().xpath(value), "xpath:" + value);
      // Vibium's SelectorOptions.text(...) is substring/semantic matching only in this version —
      // there is no separate exact-match mode, so LINKTEXT and PARTIALLINK behave identically.
      case LINKTEXT, PARTIALLINK ->
        VibiumSelector.discoveryOnly(new SelectorOptions().role("link").text(value), "linktext:" + value);
      case ACCESSIBILITY, ANDROID_UI_AUTOMATOR, IOS_CLASS_CHAIN -> throw new UnsupportedVibiumCapabilityException(
        "locator:" + strategy.getId(),
        "mobile/Appium-only locator strategy has no Vibium browser-engine equivalent"
      );
    };
  }
}
