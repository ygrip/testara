package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Row implements Serializable {
  @JsonProperty("cells")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<String> cells = new ArrayList<>();

  public Row() {
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> getCells() {
    return this.cells;
  }
}
