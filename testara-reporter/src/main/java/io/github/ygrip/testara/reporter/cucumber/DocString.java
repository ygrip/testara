package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DocString implements Serializable {
  @JsonProperty("value")
  private final String value = null;
  @JsonProperty("line")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final Integer line = null;
  @JsonProperty("content_type")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String contentType = null;

  public DocString() {
  }

  public String getValue() {
    return this.value;
  }

  public Integer getLine() {
    return this.line;
  }

  public String getContentType() {
    return this.contentType;
  }
}
