package io.github.ygrip.testara.core.error;

public class JsonException extends RuntimeException {
  private final String jsonPath;
  private final String operation;

  public JsonException(String message, String jsonPath, String operation, Throwable cause) {
    super(String.format("%s - Path: %s, Operation: %s", message, jsonPath, operation), cause);
    this.jsonPath =jsonPath;
    this.operation = operation;
  }

  public String getJsonPath() {
    return jsonPath;
  }

  public String getOperation() {
    return operation;
  }
}
