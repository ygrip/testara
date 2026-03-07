package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Location implements Serializable {
  public Integer getLine() {
    return line;
  }

  public Integer getColumn() {
    return column;
  }

  @JsonProperty("line")
  private final Integer line;
  @JsonProperty("column")
  private final Integer column;

  public Location(Integer line, Integer column) {
    this.line = line;
    this.column = column;
  }
}
