package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocString implements Serializable {
  @JsonProperty("value")
  private final String value = null;

  public DocString() {
  }

  public String getValue() {
    return this.value;
  }
}
