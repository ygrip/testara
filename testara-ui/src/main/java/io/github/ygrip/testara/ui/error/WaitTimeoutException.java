package io.github.ygrip.testara.ui.error;

/**
 * Thrown when a Testara wait condition is not satisfied within its configured timeout.
 */
public class WaitTimeoutException extends RuntimeException {

  public WaitTimeoutException(String message) {
    super(message);
  }

  public WaitTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
