package io.github.ygrip.testara.core.error;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when constructor resolution revisits a component already on the active path.
 */
public final class CircularDependencyException extends DependencyResolutionException {

  private final List<Class<?>> dependencyPath;

  public CircularDependencyException(List<Class<?>> dependencyPath) {
    super("Circular dependency detected: " + dependencyPath.stream()
      .map(Class::getSimpleName)
      .collect(Collectors.joining(" -> ")));
    this.dependencyPath = List.copyOf(dependencyPath);
  }

  public List<Class<?>> dependencyPath() {
    return dependencyPath;
  }
}
