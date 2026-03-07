package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Output implements Serializable {
  @JsonProperty("messages")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<String> messages;

  public Output(List<String> messages) {
    this.messages = messages;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> getMessages() {
    return this.messages;
  }
}
