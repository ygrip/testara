package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.Map;

import io.github.ygrip.testara.reporter.support.CommonUtil;

public class TagCoverage implements Serializable {
  private String tagName;
  private Integer testCount;
  private String successRate;
  private Map<String, Integer> countByResult;
  private Map<String, Integer> percentageByResult;
  private Long duration;
  private String durationText;

  public Map<String, Integer> getPercentageByResult() {
    return percentageByResult;
  }

  public void setPercentageByResult(Map<String, Integer> percentageByResult) {
    this.percentageByResult = percentageByResult;
  }

  public String getTagName() {
    return tagName;
  }

  public void setTagName(String tagName) {
    this.tagName = tagName;
  }

  public Integer getTestCount() {
    return testCount;
  }

  public void setTestCount(Integer testCount) {
    this.testCount = testCount;
  }

  public String getSuccessRate() {
    return successRate;
  }

  public void setSuccessRate(String successRate) {
    this.successRate = successRate;
  }

  public Map<String, Integer> getCountByResult() {
    return countByResult;
  }

  public void setCountByResult(Map<String, Integer> countByResult) {
    this.countByResult = countByResult;
  }

  public void putCountByResult(String key, Integer value) {
    this.countByResult.put(key, value);
  }

  public void putPercentageByResult(String key, Integer value) {
    this.percentageByResult.put(key, value);
  }

  public void setDuration(Long duration){
    this.duration = duration;
  }

  public Long getDuration(){
    return this.duration;
  }

  public String getDurationText(){
    return this.duration == null || this.duration <= 0 ? "0ms" : CommonUtil.formatDuration(this.duration);
  }
}
