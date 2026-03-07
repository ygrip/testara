package io.github.ygrip.testara.validation.support;

import io.github.ygrip.testara.validation.model.FailingRunnable;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public final class AssertUtils {

  /**
   * <p>assertDoesNotThrow.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param action       a {@link io.github.ygrip.testara.validation.model.FailingRunnable} object.
   * @throws AssertionError if any.
   */
  public static void assertDoesNotThrow(String errorMessage, FailingRunnable action) throws AssertionError {
    try {
      action.run();
    } catch (Exception ex) {
      errorMessage = StringUtils.isBlank(errorMessage) ? "expected action not to throw exception" : errorMessage;
      throw new AssertionError(String.format("%s :\n%s", errorMessage, ex.getMessage()));
    }
  }

  /**
   * <p>assertDoesNotThrow.</p>
   *
   * @param action a {@link io.github.ygrip.testara.validation.model.FailingRunnable} object.
   * @throws AssertionError if any.
   */
  public static void assertDoesNotThrow(FailingRunnable action) throws AssertionError {
    assertDoesNotThrow("expected action not to throw exception", action);
  }

  /**
   * <p>assertDoesThrow.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param action       a {@link io.github.ygrip.testara.validation.model.FailingRunnable} object.
   * @throws AssertionError if any.
   */
  public static void assertDoesThrow(String errorMessage, FailingRunnable action) throws AssertionError {
    Exception exception = null;
    AssertionError error = null;
    try {
      action.run();
    } catch (AssertionError ae) {
      error = ae;
    } catch (Exception ex) {
      exception = ex;
    }

    if (ObjectUtils.isEmpty(error) && ObjectUtils.isEmpty(exception)) {
      errorMessage = StringUtils.isBlank(errorMessage) ? "expected action should throw exception" : errorMessage;
      throw new AssertionError(String.format("%s", errorMessage));
    }
  }

  /**
   * <p>assertDoesThrow.</p>
   *
   * @param action a {@link io.github.ygrip.testara.validation.model.FailingRunnable} object.
   * @throws AssertionError if any.
   */
  public static void assertDoesThrow(FailingRunnable action) throws AssertionError {
    assertDoesThrow("expected action should throw exception", action);
  }
}
