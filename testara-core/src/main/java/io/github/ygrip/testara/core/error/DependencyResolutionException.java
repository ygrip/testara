package io.github.ygrip.testara.core.error;

/**
 * Base type for failures produced while resolving a component graph.
 * Extending {@link IllegalStateException} preserves compatibility with callers
 * that previously handled missing registrations as an illegal framework state.
 */
public class DependencyResolutionException extends IllegalStateException {

  public DependencyResolutionException(String message) {
    super(message);
  }

  public DependencyResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}

