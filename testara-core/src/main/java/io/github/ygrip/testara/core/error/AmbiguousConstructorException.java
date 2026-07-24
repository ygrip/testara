package io.github.ygrip.testara.core.error;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Raised when a component has multiple constructors and none is selected explicitly.
 */
public final class AmbiguousConstructorException extends DependencyResolutionException {

  public AmbiguousConstructorException(Class<?> type, Constructor<?>[] constructors) {
    super(buildMessage(type, constructors));
  }

  private static String buildMessage(Class<?> type, Constructor<?>[] constructors) {
    String signatures = Arrays.stream(constructors)
      .sorted(Comparator.comparing(Constructor::toGenericString))
      .map(Constructor::toGenericString)
      .map(signature -> "  " + signature)
      .collect(Collectors.joining(System.lineSeparator()));

    return "Ambiguous constructors for " + type.getName() + ":" + System.lineSeparator() + signatures
      + System.lineSeparator() + "Annotate exactly one constructor with @Inject.";
  }
}

