package io.github.ygrip.testara.core.error;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("error")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ErrorTests extends BaseTests {

  // ==================== InvalidConfigurationPropertiesException tests ====================

  @Test
  public void invalidConfigurationPropertiesException_withMessage_shouldSetMessage() {
    String errorMessage = "Configuration property is missing";
    InvalidConfigurationPropertiesException exception =
        new InvalidConfigurationPropertiesException(errorMessage);

    assertThat(exception.getMessage(), equalTo(errorMessage));
    assertThat(exception.getCause(), is(nullValue()));
  }

  @Test
  public void invalidConfigurationPropertiesException_withMessageAndCause_shouldSetBoth() {
    String errorMessage = "Configuration property is invalid";
    Throwable cause = new IllegalArgumentException("Invalid value");

    InvalidConfigurationPropertiesException exception =
        new InvalidConfigurationPropertiesException(errorMessage, cause);

    assertThat(exception.getMessage(), equalTo(errorMessage));
    assertThat(exception.getCause(), is(cause));
    assertThat(exception.getCause(), instanceOf(IllegalArgumentException.class));
  }

  @Test
  public void invalidConfigurationPropertiesException_shouldBeRuntimeException() {
    InvalidConfigurationPropertiesException exception =
        new InvalidConfigurationPropertiesException("test");

    assertThat(exception, instanceOf(RuntimeException.class));
  }

  @Test
  public void invalidConfigurationPropertiesException_canBeThrownAndCaught() {
    String errorMessage = "Test error";

    try {
      throw new InvalidConfigurationPropertiesException(errorMessage);
    } catch (InvalidConfigurationPropertiesException e) {
      assertThat(e.getMessage(), equalTo(errorMessage));
    }
  }

  @Test
  public void invalidConfigurationPropertiesException_withNullCause_shouldWork() {
    InvalidConfigurationPropertiesException exception =
        new InvalidConfigurationPropertiesException("error", null);

    assertThat(exception.getMessage(), equalTo("error"));
    assertThat(exception.getCause(), is(nullValue()));
  }

  // ==================== JsonException tests ====================

  @Test
  public void jsonException_shouldSetAllFields() {
    String message = "Failed to parse JSON";
    String jsonPath = "$.data.items[0]";
    String operation = "read";
    Throwable cause = new RuntimeException("Parse error");

    JsonException exception = new JsonException(message, jsonPath, operation, cause);

    assertThat(exception.getJsonPath(), equalTo(jsonPath));
    assertThat(exception.getOperation(), equalTo(operation));
    assertThat(exception.getCause(), is(cause));
  }

  @Test
  public void jsonException_shouldFormatMessageCorrectly() {
    String message = "Failed to parse";
    String jsonPath = "$.root";
    String operation = "update";
    Throwable cause = new RuntimeException("Error");

    JsonException exception = new JsonException(message, jsonPath, operation, cause);

    String expectedMessage = String.format("%s - Path: %s, Operation: %s", message, jsonPath, operation);
    assertThat(exception.getMessage(), equalTo(expectedMessage));
  }

  @Test
  public void jsonException_shouldBeRuntimeException() {
    JsonException exception = new JsonException("msg", "path", "op", null);

    assertThat(exception, instanceOf(RuntimeException.class));
  }

  @Test
  public void jsonException_getJsonPath_shouldReturnPath() {
    JsonException exception = new JsonException("msg", "$.my.path", "read", null);

    assertThat(exception.getJsonPath(), equalTo("$.my.path"));
  }

  @Test
  public void jsonException_getOperation_shouldReturnOperation() {
    JsonException exception = new JsonException("msg", "path", "write", null);

    assertThat(exception.getOperation(), equalTo("write"));
  }

  @Test
  public void jsonException_canBeThrownAndCaught() {
    try {
      throw new JsonException("Test", "$.test", "delete", null);
    } catch (JsonException e) {
      assertThat(e.getJsonPath(), equalTo("$.test"));
      assertThat(e.getOperation(), equalTo("delete"));
    }
  }

  @Test
  public void jsonException_withNullPath_shouldWork() {
    JsonException exception = new JsonException("msg", null, "read", null);

    assertThat(exception.getJsonPath(), is(nullValue()));
    assertThat(exception.getMessage(), containsString("null"));
  }

  @Test
  public void jsonException_withComplexPath_shouldPreservePath() {
    String complexPath = "$.data[*].items[?(@.type=='active')].values[0]";
    JsonException exception = new JsonException("error", complexPath, "query", null);

    assertThat(exception.getJsonPath(), equalTo(complexPath));
  }

  @Test
  public void jsonException_withNestedCause_shouldPreserveCauseChain() {
    Throwable rootCause = new NullPointerException("null value");
    Throwable middleCause = new IllegalStateException("bad state", rootCause);
    JsonException exception = new JsonException("error", "path", "op", middleCause);

    assertThat(exception.getCause(), is(middleCause));
    assertThat(exception.getCause().getCause(), is(rootCause));
  }
}
