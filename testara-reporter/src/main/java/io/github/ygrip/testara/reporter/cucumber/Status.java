package io.github.ygrip.testara.reporter.cucumber;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
  PASSED, FAILED, SKIPPED, PENDING, UNDEFINED;

  @Override
  public String toString() {
    return this.name().toLowerCase();
  }

  public String getRawName() {
    return this.name().toLowerCase();
  }

  public String getLabel() {
    return this.name().substring(0, 1).toUpperCase() + this.name().substring(1).toLowerCase();
  }

  public boolean isPassed() {
    return this == PASSED;
  }

  @JsonValue
  public String toLower() {
    return this.toString().toLowerCase();
  }
}
