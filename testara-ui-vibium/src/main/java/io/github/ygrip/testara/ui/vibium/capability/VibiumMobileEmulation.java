package io.github.ygrip.testara.ui.vibium.capability;

import io.github.ygrip.testara.ui.page.Element;

/**
 * Vibium-specific mobile-web emulation surface: touch tap, CSS media emulation, and geolocation
 * override. There is no engine-neutral Testara capability interface for this — this module's
 * implementation plan §13 documents these as real Vibium Java primitives with no equivalent core
 * abstraction (unlike the five engine-neutral capabilities), so this is a Vibium-only addition,
 * dispatched the same way as the others via {@code VibiumSession#capability(VibiumMobileEmulation.class)}.
 *
 * <p>Deliberately does NOT expose device-pixel-ratio, user-agent override, or a named device
 * profile: plan §13 records that none of these have a supported Java option in this pinned client
 * (26.5.31) — {@link #capabilityReport()} is how a caller discovers this, rather than a rejected
 * config property that would exist only to be refused.
 */
public interface VibiumMobileEmulation {

  /**
   * Dispatches a real touch tap on the resolved element via {@code com.vibium.Element#tap()}
   * (confirmed via {@code javap} against {@code vibium-26.5.31.jar}: it takes no coordinates and
   * taps the element itself, unlike {@code Page#touch()}'s raw-coordinate {@code Touch#tap(double,
   * double)}). Follows this module's usual gate: only an interaction-safe (CSS-derived) locator can
   * be tapped.
   */
  VibiumMobileEmulation tap(Element locator);

  /**
   * Applies a CSS media/color-scheme/reduced-motion/forced-colors/contrast override via {@code
   * Page#emulateMedia(MediaOptions)}. Only the non-blank fields present on {@code emulation} are
   * forwarded; a {@code null}/blank field is left at Vibium's default (unset).
   */
  VibiumMobileEmulation emulateMedia(VibiumMediaEmulation emulation);

  /**
   * Applies a geolocation override via {@code Page#setGeolocation(GeoCoords)}. {@code accuracy} is
   * optional (Vibium's {@code GeoCoords} models it as a separate fluent setter, not a third
   * constructor argument, confirmed via {@code javap}); pass {@code null} to leave it unset.
   */
  VibiumMobileEmulation setGeolocation(double latitude, double longitude, Double accuracy);

  /** Reports which mobile-web emulation primitives this pinned Vibium client genuinely supports. */
  VibiumMobileCapabilityReport capabilityReport();
}
