package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

public class FailedFeature implements Comparable<FailedFeature>, Serializable {
  private String name;
  private Integer failurePercentage;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getFailurePercentage() {
    return failurePercentage;
  }

  public void setFailurePercentage(Integer failurePercentage) {
    this.failurePercentage = failurePercentage;
  }

  @Override
  public int compareTo(FailedFeature o) {
    return this.getFailurePercentage().compareTo(o.getFailurePercentage());
  }
}
