package io.github.ygrip.testara.core.error;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when a selected component constructor cannot be invoked.
 */
public class ComponentCreationException extends DependencyResolutionException {

  public ComponentCreationException(Class<?> type, List<Class<?>> dependencyPath, Throwable cause) {
    super(buildMessage(type, dependencyPath), cause);
  }

  private static String buildMessage(Class<?> type, List<Class<?>> dependencyPath) {
    String path = dependencyPath.stream()
      .map(Class::getSimpleName)
      .collect(Collectors.joining(" -> "));
    return "Failed to create component " + type.getName() + (path.isEmpty() ? "" : ". Resolution path: " + path);
  }
}
