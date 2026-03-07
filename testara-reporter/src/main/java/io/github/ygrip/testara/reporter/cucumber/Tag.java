package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Tag implements Serializable {
  @JsonProperty("name")
  private final String name;

  public void setType(String type) {
    this.type = type;
  }

  public void setLocation(Location location) {
    this.location = location;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("type")
  private String type;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("location")
  private Location location;

  public Tag(String name) {
    this.name = name;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getType() {
    return type;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Location getLocation() {
    return location;
  }

  public String getName() {
    return this.name;
  }
}
