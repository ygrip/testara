package io.github.ygrip.testara.reporter.model;

import java.util.Map;

public class AggregateSummary {
  public Integer getTotalScenarios() {
    return totalScenarios;
  }

  public void setTotalScenarios(Integer totalScenarios) {
    this.totalScenarios = totalScenarios;
  }

  public Integer getTotalSteps() {
    return totalSteps;
  }

  public void setTotalSteps(Integer totalSteps) {
    this.totalSteps = totalSteps;
  }

  public String getFastestTest() {
    return fastestTest;
  }

  public void setFastestTest(String fastestTest) {
    this.fastestTest = fastestTest;
  }

  public String getSlowestTest() {
    return slowestTest;
  }

  public void setSlowestTest(String slowestTest) {
    this.slowestTest = slowestTest;
  }

  public String getTotalExecutionTime() {
    return totalExecutionTime;
  }

  public void setTotalExecutionTime(String totalExecutionTime) {
    this.totalExecutionTime = totalExecutionTime;
  }

  public Map<String, Integer> getSummary() {
    return summary;
  }

  public void setSummary(Map<String, Integer> summary) {
    this.summary = summary;
  }

  private Integer totalScenarios;
  private Integer totalSteps;
  private String fastestTest;
  private String slowestTest;
  private String totalExecutionTime;
  private Map<String, Integer> summary;
}
