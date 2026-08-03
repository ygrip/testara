package io.github.ygrip.testara.ui.vibium.capability;

/**
 * Immutable pass-through of {@code com.vibium.types.MediaOptions}' real field list, confirmed via
 * {@code javap} against {@code vibium-26.5.31.jar}: {@code media}, {@code colorScheme}, {@code
 * reducedMotion}, {@code forcedColors}, and {@code contrast} — all five are real fluent setters on
 * that native type, not just {@code colorScheme}. Any field left {@code null} (or blank) here is
 * not forwarded to {@code MediaOptions}, leaving that aspect at Vibium's own default.
 */
public record VibiumMediaEmulation(
    String media,
    String colorScheme,
    String reducedMotion,
    String forcedColors,
    String contrast
) {

  /** Convenience factory for the common single-field case used by plan §13's mobile-web contract. */
  public static VibiumMediaEmulation colorScheme(String colorScheme) {
    return new VibiumMediaEmulation(null, colorScheme, null, null, null);
  }
}
