package io.github.ygrip.testara.reporter.cucumber;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Match implements Serializable {
  @JsonProperty("location")
  private final String location = null;
  @JsonProperty("arguments")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Argument> arguments = new ArrayList<>();

  public String getLocation() {
    return this.location;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Argument> getArguments() {
    return this.arguments;
  }
}
