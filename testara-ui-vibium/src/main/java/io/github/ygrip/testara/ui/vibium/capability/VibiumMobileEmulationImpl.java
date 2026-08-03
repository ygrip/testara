package io.github.ygrip.testara.ui.vibium.capability;

import org.apache.commons.lang3.StringUtils;

import com.vibium.errors.VibiumException;
import com.vibium.types.GeoCoords;
import com.vibium.types.MediaOptions;

import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;

/**
 * Vibium's {@link VibiumMobileEmulation}.
 *
 * <p>Real-API findings (verified with {@code javap} against {@code vibium-26.5.31.jar}) that shape
 * this class:
 * <ul>
 *   <li>{@code com.vibium.types.MediaOptions} genuinely has five fields — {@code media}, {@code
 *       colorScheme}, {@code reducedMotion}, {@code forcedColors}, {@code contrast} — all with real
 *       fluent setters; all five are forwarded via {@link #emulateMedia}, not just color-scheme.
 *   <li>{@code com.vibium.types.GeoCoords(double, double)} takes latitude/longitude only; {@code
 *       accuracy} is a separate fluent {@code GeoCoords#accuracy(double)} setter.
 * </ul>
 */
public final class VibiumMobileEmulationImpl extends VibiumElementResolver implements VibiumMobileEmulation {

  public VibiumMobileEmulationImpl(VibiumSession session) {
    super(session);
  }

  @Override
  public VibiumMobileEmulation tap(Element locator) {
    VibiumElement el = requireElement(locator, "tap");
    el.requireInteractionSafe("tap");
    try {
      el.raw()
        .tap();
    } catch (VibiumException e) {
      throw wrap("tap", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public VibiumMobileEmulation emulateMedia(VibiumMediaEmulation emulation) {
    MediaOptions options = new MediaOptions();
    if (emulation != null) {
      if (StringUtils.isNotBlank(emulation.media())) {
        options = options.media(emulation.media());
      }
      if (StringUtils.isNotBlank(emulation.colorScheme())) {
        options = options.colorScheme(emulation.colorScheme());
      }
      if (StringUtils.isNotBlank(emulation.reducedMotion())) {
        options = options.reducedMotion(emulation.reducedMotion());
      }
      if (StringUtils.isNotBlank(emulation.forcedColors())) {
        options = options.forcedColors(emulation.forcedColors());
      }
      if (StringUtils.isNotBlank(emulation.contrast())) {
        options = options.contrast(emulation.contrast());
      }
    }
    try {
      session.pageForApi()
        .emulateMedia(options);
    } catch (VibiumException e) {
      throw wrap("emulateMedia", "n/a", e);
    }
    return this;
  }

  @Override
  public VibiumMobileEmulation setGeolocation(double latitude, double longitude, Double accuracy) {
    GeoCoords coords = new GeoCoords(latitude, longitude);
    if (accuracy != null) {
      coords = coords.accuracy(accuracy);
    }
    try {
      session.pageForApi()
        .setGeolocation(coords);
    } catch (VibiumException e) {
      throw wrap("setGeolocation", "n/a", e);
    }
    return this;
  }

  @Override
  public VibiumMobileCapabilityReport capabilityReport() {
    return VibiumMobileCapabilityReport.forThisClient();
  }

  private VibiumOperationException wrap(String operation, String locatorDescription, VibiumException cause) {
    return VibiumOperationException.of(operation, locatorDescription, safePageUrl(), 0L, cause);
  }
}
