package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Argument implements Serializable {
  @JsonProperty("rows")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Row> rows = new ArrayList<>();
  @JsonProperty("val")
  private final String val = null;
  @JsonProperty("offset")
  private final Integer offset = null;

  public Argument() {
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Row> getRows() {
    return this.rows;
  }

  public String getVal() {
    return this.val;
  }

  public Integer getOffset() {
    return this.offset;
  }
}
