package io.github.ygrip.testara.ui.vibium.error;

/**
 * Thrown when a Vibium browser/page operation fails for reasons unrelated to element resolution
 * (e.g. navigation, session/page lifecycle). Core has no shared base exception type for this
 * family ({@code io.github.ygrip.testara.ui.error.PageFailureException} directly extends
 * {@code RuntimeException}), so this mirrors that convention.
 */
public class VibiumOperationException extends RuntimeException {

  public VibiumOperationException(String message) {
    super(message);
  }

  public VibiumOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Structured failure for a native Vibium operation, used to rewrap any
   * {@code com.vibium.errors.*} exception bubbling out of a real Vibium API call. {@code
   * locatorDescription} should be {@code "n/a"} for operations with no element target (e.g. tab
   * management), and {@code timeoutMs} should be {@code 0} for a single-shot call with no
   * wait/retry budget. Callers must keep {@code locatorDescription}/{@code pageUrl} free of
   * cookie values, authorization headers, typed passwords, or page HTML.
   *
   * <p>Message shape: {@code Vibium operation '<operation>' failed for locator '<locator
   * description>' on '<page URL>' after <timeout>ms: <original Vibium error type + message>}.
   */
  public static VibiumOperationException of(
    String operation, String locatorDescription, String pageUrl, long timeoutMs, Throwable cause
  ) {
    String message = String.format(
      "Vibium operation '%s' failed for locator '%s' on '%s' after %dms: %s",
      operation,
      locatorDescription,
      pageUrl,
      timeoutMs,
      describeCause(cause)
    );
    return new VibiumOperationException(message, cause);
  }

  private static String describeCause(Throwable cause) {
    if (cause == null) {
      return "unknown error";
    }
    return cause.getClass()
      .getName() + ": " + cause.getMessage();
  }
}
