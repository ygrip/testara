package io.github.ygrip.testara.core.error;

/**
 * Raised when a component marked for interception cannot be proxied safely.
 */
public final class ProxyCreationException extends DependencyResolutionException {

  public ProxyCreationException(String message) {
    super(message);
  }

  public ProxyCreationException(String message, Throwable cause) {
    super(message, cause);
  }
}
