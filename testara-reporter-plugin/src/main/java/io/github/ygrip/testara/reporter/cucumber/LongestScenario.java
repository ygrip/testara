package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import io.github.ygrip.testara.reporter.support.CommonUtil;

public class LongestScenario implements Comparable<LongestScenario>, Serializable {
  private String name;
  private Long duration;
  private Integer percentage;
  private String durationRange;

  public Integer getPercentage() {
    return percentage;
  }

  public void setPercentage(Integer percentage) {
    this.percentage = percentage;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public int compareTo(LongestScenario o) {
    return getDuration().compareTo(o.getDuration());
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(Long duration) {
    this.durationRange = CommonUtil.formatDuration(duration);
    this.duration = duration;
  }

  public String getDurationRange() {
    return this.durationRange;
  }
}
