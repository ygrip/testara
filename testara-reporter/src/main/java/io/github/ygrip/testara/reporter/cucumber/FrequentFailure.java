package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

public class FrequentFailure implements Comparable<FrequentFailure>, Serializable {
  private String name;

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  private String error;
  private Integer count;
  private String resultClass;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public String getResultClass() {
    return resultClass;
  }

  public void setResultClass(String resultClass) {
    this.resultClass = resultClass;
  }

  @Override
  public int compareTo(FrequentFailure o) {
    return this.getCount().compareTo(o.getCount());
  }
}
