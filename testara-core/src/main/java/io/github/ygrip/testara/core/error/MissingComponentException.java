package io.github.ygrip.testara.core.error;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when no provider has been registered for a constructor dependency.
 */
public final class MissingComponentException extends DependencyResolutionException {

  private final Class<?> componentType;
  private final List<Class<?>> dependencyPath;

  public MissingComponentException(Class<?> componentType) {
    this(componentType, List.of(), null);
  }

  public MissingComponentException(Class<?> componentType, List<Class<?>> dependencyPath, Throwable cause) {
    super(buildMessage(componentType, dependencyPath), cause);
    this.componentType = componentType;
    this.dependencyPath = List.copyOf(dependencyPath);
  }

  private static String buildMessage(Class<?> componentType, List<Class<?>> dependencyPath) {
    String message = "No component registered for " + componentType.getName();
    if (dependencyPath.isEmpty()) {
      return message;
    }
    return message + ". Resolution path: " + dependencyPath.stream()
      .map(Class::getSimpleName)
      .collect(Collectors.joining(" -> "));
  }

  public Class<?> componentType() {
    return componentType;
  }

  public List<Class<?>> dependencyPath() {
    return dependencyPath;
  }
}

