package io.github.ygrip.testara.engine.support;

import java.util.Arrays;
import java.util.List;

public final class ExceptionHandler {
  private static final List<String> IGNORED_STACKTRACE_PREFIXES = List.of(
      "org.junit.",
      "org.opentest4j.",
      "io.cucumber.",
      "org.assertj.",
      "org.hamcrest.",
      "org.apiguardian.",
      "org.springframework.test.",
      "java.lang.reflect.",
      "sun.reflect.",
      "jdk.internal.reflect.",
      "java.util.concurrent.",
      "org.junit.platform."
  );

  /**
   * Trims stack traces by:
   * 1. Filtering out framework/internal stack frames.
   * 2. Recursively cleaning causes.
   * 3. Unwrapping parent exceptions that only wrap AssertJ or framework exceptions.
   */
  public static Throwable trimStackTrace(Throwable error) {
    if (error == null) return null;

    // --- 1. Unwrap framework wrapper exceptions ---
    // Common case: AssertionFailedError -> AssertJMultipleFailuresError
    Throwable cause = error.getCause();
    if (cause != null && cause != error) {
      String parentClass = error.getClass().getName();
      String causeClass = cause.getClass().getName();

      boolean parentIsJUnitOrCucumber = IGNORED_STACKTRACE_PREFIXES.stream()
          .anyMatch(prefix -> parentClass.startsWith(prefix));
      boolean causeIsAssertJ = causeClass.startsWith("org.assertj.");

      // If parent is just a test framework wrapper around an AssertJ error, strip it
      if (parentIsJUnitOrCucumber && causeIsAssertJ) {
        error = cause;
      }
    }

    // --- 2. Filter stack trace frames ---
    StackTraceElement[] original = error.getStackTrace();
    StackTraceElement[] filtered = Arrays.stream(original)
        .filter(ste -> IGNORED_STACKTRACE_PREFIXES.stream()
            .noneMatch(prefix -> ste.getClassName().startsWith(prefix)))
        .toArray(StackTraceElement[]::new);

    error.setStackTrace(filtered);

    // --- 3. Recurse for causes ---
    if (error.getCause() != null && error.getCause() != error) {
      trimStackTrace(error.getCause());
    }

    return error;
  }
}
