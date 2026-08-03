package io.github.ygrip.testara.ui.vibium.error;

/**
 * Thrown when a Vibium element/locator cannot be resolved for reasons other than a plain
 * zero-match (which is instead surfaced as {@code null}/empty-list per the finder contract — see
 * {@link io.github.ygrip.testara.ui.vibium.locator.VibiumSelector}). Core has no shared base
 * exception type for this family ({@code io.github.ygrip.testara.ui.error.ElementNotFoundException}
 * directly extends {@code RuntimeException}), so this mirrors that convention.
 */
public class VibiumElementResolutionException extends RuntimeException {

  public VibiumElementResolutionException(String message) {
    super(message);
  }

  public VibiumElementResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
