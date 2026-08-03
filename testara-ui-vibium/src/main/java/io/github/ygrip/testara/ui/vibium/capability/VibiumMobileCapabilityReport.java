package io.github.ygrip.testara.ui.vibium.capability;

/**
 * Honest capability report for {@link VibiumMobileEmulation}, per this module's implementation
 * plan §13 point 4 ("Expose a session capability report so tests can query viewport, touch, media,
 * geolocation, and dpr support"). {@code devicePixelRatio}, {@code userAgentOverride}, and {@code
 * namedDeviceProfile} are always {@code false}: this pinned Vibium Java client (26.5.31) has no
 * supported option for any of them (plan §13's evidence table), so there is deliberately no
 * rejected config property standing in for them either — this report itself is the "reject rather
 * than silently approximate" boundary (plan §13 point 5).
 */
public record VibiumMobileCapabilityReport(
    boolean viewport,
    boolean touch,
    boolean media,
    boolean geolocation,
    boolean devicePixelRatio,
    boolean userAgentOverride,
    boolean namedDeviceProfile
) {

  /** The genuine support matrix for Vibium 26.5.31, per this module's implementation plan §13. */
  public static VibiumMobileCapabilityReport forThisClient() {
    return new VibiumMobileCapabilityReport(true, true, true, true, false, false, false);
  }
}
