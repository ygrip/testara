package io.github.ygrip.testara.ui.vibium.error;

/**
 * Thrown when a Testara operation has no supported Vibium Java client equivalent (e.g. local
 * outbound proxy configuration, or a capability adapter not yet implemented). Never log-and-return
 * null for an unsupported operation; throw this instead so the failure is explicit.
 */
public class UnsupportedVibiumCapabilityException extends RuntimeException {

  public UnsupportedVibiumCapabilityException(String operation, String suggestedAlternative) {
    super(formatMessage(operation, suggestedAlternative));
  }

  public UnsupportedVibiumCapabilityException(String operation, String suggestedAlternative, Throwable cause) {
    super(formatMessage(operation, suggestedAlternative), cause);
  }

  private static String formatMessage(String operation, String suggestedAlternative) {
    return String.format("Vibium operation '%s' is not supported: %s", operation, suggestedAlternative);
  }
}
