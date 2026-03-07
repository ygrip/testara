package io.github.ygrip.testara.validation.model;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.support.CommonHelper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>Abstract ValidatorLogic class.</p>
 *
 * @author yunaz.ramadhan on 12/9/2019
 * @version $Id: $Id
 */
@Log4j2
public abstract class ValidatorLogic<ACTUAL, EXPECTED> {
  private final TypeReference<ACTUAL> actualType;
  private final TypeReference<EXPECTED> expectedType;
  private final StackTraceElement stackTraceElement;
  private final ValidatorInfo info;
  private ACTUAL actual;
  private EXPECTED expected;
  private String reason;
  private List<String> additionalMessages;

  /**
   * <p>Constructor for ValidatorLogic.</p>
   */
  public ValidatorLogic() {
    this.actualType = constructActualType();
    this.expectedType = constructExpectedType();
    this.stackTraceElement = new StackTraceElement(this.getClass().getName(),
        "validate",
        String.format("%s.java", this.getClass().getSimpleName()),
        1);
    this.info = new ValidatorInfo(this.getClass());
    this.additionalMessages = Collections.emptyList();
  }

  /**
   * Method that will return validation info, info or metadata is build from the CommandTag annotation used by the ValidatorLogic instance
   *
   * @return default command info
   */
  public ValidatorInfo info() {
    return this.info;
  }

  private TypeReference<ACTUAL> constructActualType() {
    Class<?> clazz = getClass();
    while (!Modifier.isAbstract(clazz.getSuperclass().getModifiers())) {
      clazz = clazz.getSuperclass();
    }

    Class<?> finalClazz = clazz;
    return new TypeReference<>() {
      @Override
      public Type getType() {
        return CommonHelper.getParameterizedType(finalClazz, 0);
      }
    };
  }

  private TypeReference<EXPECTED> constructExpectedType() {
    Class<?> clazz = getClass();
    while (!Modifier.isAbstract(clazz.getSuperclass().getModifiers())) {
      clazz = clazz.getSuperclass();
    }
    Class<?> finalClazz = clazz;
    return new TypeReference<>() {
      @Override
      public Type getType() {
        return CommonHelper.getParameterizedType(finalClazz, 1);
      }
    };
  }

  /**
   * <p>Getter for the field <code>actual</code>.</p>
   *
   * @return a ACTUAL object.
   */
  public ACTUAL getActual() {
    return actual;
  }

  /**
   * <p>Setter for the field <code>actual</code>.</p>
   *
   * @param actual a ACTUAL object.
   * @return a {@link io.github.ygrip.testara.validation.model.ValidatorLogic} object.
   */
  public ValidatorLogic<ACTUAL, EXPECTED> setActual(ACTUAL actual) {
    this.actual = actual;
    return this;
  }

  /**
   * <p>Getter for the field <code>expected</code>.</p>
   *
   * @return a EXPECTED object.
   */
  public EXPECTED getExpected() {
    return expected;
  }

  /**
   * <p>Setter for the field <code>expected</code>.</p>
   *
   * @param expected a EXPECTED object.
   * @return a {@link io.github.ygrip.testara.validation.model.ValidatorLogic} object.
   */
  public ValidatorLogic<ACTUAL, EXPECTED> setExpected(EXPECTED expected) {
    this.expected = expected;
    return this;
  }

  /**
   * <p>Getter for the field <code>reason</code>.</p>
   *
   * @return a {@link String} object.
   */
  public String getReason() {
    return reason;
  }

  /**
   * <p>Setter for the field <code>reason</code>.</p>
   *
   * @param reason a {@link String} object.
   * @return a {@link io.github.ygrip.testara.validation.model.ValidatorLogic} object.
   */
  public ValidatorLogic<ACTUAL, EXPECTED> setReason(String reason) {
    this.reason = reason;
    return this;
  }

  /**
   * <p>addMessage.</p>
   *
   * @param message a {@link String} object.
   * @return a {@link io.github.ygrip.testara.validation.model.ValidatorLogic} object.
   */
  public ValidatorLogic<ACTUAL, EXPECTED> addMessage(String message) {
    if (ObjectUtils.isEmpty(additionalMessages)) {
      additionalMessages = new ArrayList<>();
    }
    additionalMessages.add(message);
    return this;
  }

  /**
   * <p>setAdditionalMessage.</p>
   *
   * @param messages a {@link List} object.
   * @return a {@link io.github.ygrip.testara.validation.model.ValidatorLogic} object.
   */
  public ValidatorLogic<ACTUAL, EXPECTED> setAdditionalMessage(List<String> messages) {
    additionalMessages = messages;
    return this;
  }

  /**
   * <p>Getter for the field <code>additionalMessages</code>.</p>
   *
   * @return a {@link List} object.
   */
  public List<String> getAdditionalMessages() {
    if (ObjectUtils.isEmpty(this.additionalMessages)) {
      this.additionalMessages = new ArrayList<>();
    }
    return this.additionalMessages;
  }

  /**
   * <p>setDefaultMessage.</p>
   *
   * @return a {@link String} object.
   */
  protected abstract String setDefaultMessage();

  /**
   * <p>Getter for the field <code>actualType</code>.</p>
   *
   * @return a {@link TypeReference} object.
   */
  public TypeReference<ACTUAL> getActualType() {
    return this.actualType;
  }

  /**
   * <p>Getter for the field <code>expectedType</code>.</p>
   *
   * @return a {@link TypeReference} object.
   */
  public TypeReference<EXPECTED> getExpectedType() {
    return this.expectedType;
  }

  /**
   * <p>validate.</p>
   *
   * @return a boolean.
   * @throws Exception if any.
   */
  public abstract boolean validate() throws Exception;

  /**
   * <p>constructDataToCollections.</p>
   *
   * @param input a {@link Object} object.
   * @return a {@link List} object.
   */
  public List<?> constructDataToCollections(Object input) {
    if (ObjectUtils.isEmpty(input)) {
      return new ArrayList<>();
    }
    if (input instanceof List) {
      return (List<?>) input;
    } else if (input instanceof Collection) {
      return new ArrayList<>((Collection<?>) input);
    } else if (input.getClass().isArray()) {
      return Arrays.stream((Object[]) input).collect(Collectors.toList());
    } else {
      return Collections.singletonList(input);
    }
  }

  /**
   * <p>areEqual.</p>
   *
   * @param actual   a {@link Object} object.
   * @param expected a {@link Object} object.
   * @return a boolean.
   */
  public boolean areEqual(Object actual, Object expected) {
    if (actual == null) {
      return expected == null;
    }

    if (expected != null && isArray(actual)) {
      return isArray(expected) && areArraysEqual(actual, expected);
    }

    return actual.equals(expected);
  }

  /**
   * <p>areArraysEqual.</p>
   *
   * @param actualArray   a {@link Object} object.
   * @param expectedArray a {@link Object} object.
   * @return a boolean.
   */
  public boolean areArraysEqual(Object actualArray, Object expectedArray) {
    return areArrayLengthsEqual(actualArray, expectedArray) && areArrayElementsEqual(actualArray, expectedArray);
  }

  /**
   * <p>areArrayLengthsEqual.</p>
   *
   * @param actualArray   a {@link Object} object.
   * @param expectedArray a {@link Object} object.
   * @return a boolean.
   */
  public boolean areArrayLengthsEqual(Object actualArray, Object expectedArray) {
    return Array.getLength(actualArray) == Array.getLength(expectedArray);
  }

  /**
   * <p>areArrayElementsEqual.</p>
   *
   * @param actualArray   a {@link Object} object.
   * @param expectedArray a {@link Object} object.
   * @return a boolean.
   */
  public boolean areArrayElementsEqual(Object actualArray, Object expectedArray) {
    for (int i = 0; i < Array.getLength(actualArray); i++) {
      if (!areEqual(Array.get(actualArray, i), Array.get(expectedArray, i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * <p>isArray.</p>
   *
   * @param o a {@link Object} object.
   * @return a boolean.
   */
  public boolean isArray(Object o) {
    return o.getClass().isArray();
  }

  /**
   * <p>result.</p>
   *
   * @return a {@link io.github.ygrip.testara.validation.model.ValidatorResult} object.
   */
  public ValidatorResult result() {
    log.debug("#Process validation {}", this.getClass());
    boolean valid = false;
    try {
      valid = validate();
    } catch (Exception e) {
      setReason(e.getMessage());
      log.trace("#ERROR exception during {}, log {}", this.getClass().getSimpleName(), e);
    }
    if (StringUtils.isBlank(getReason())) {
      setReason(setDefaultMessage());
    }
    Throwable exception = null;
    try {
      if (!valid) {
        exception = fillStackTraces(new Exception(getFormattedReason(false)));
      }
    } catch (Exception ignored) {

    }
    ValidatorResult response =
        ValidatorResult.builder().validation(info().name()).success(valid).error(valid ? null : exception).build();
    reset();
    return response;
  }

  private String getFormattedReason(boolean valid) {
    return valid ?
        String.format("%s is success", info().name()) :
        String.format("%s is failed because : %s :\n<actual> : \n%s\n<expected> : \n%s\n",
            info().name(),
            getReason(),
            getActual(),
            getExpected());
  }

  private Throwable fillStackTraces(Throwable error) {
    StackTraceElement[] stacktraces = error.getStackTrace();
    List<StackTraceElement> elements = new ArrayList<>();
    elements.add(this.stackTraceElement);
    elements.addAll(Arrays.asList(stacktraces));

    error.setStackTrace(elements.toArray(StackTraceElement[]::new));
    return error;
  }

  private void reset() {
    setReason(null);
    setActual(null);
    setExpected(null);
    setAdditionalMessage(new ArrayList<>());
  }
}
