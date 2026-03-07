package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import io.github.ygrip.testara.reporter.support.CommonUtil;

public class LongestStep implements Comparable<LongestStep>, Serializable {
  private String name;
  private Integer count;
  private Long minDuration;
  private Long maxDuration;
  private Integer percentage;
  private String durationRange;

  public Integer getPercentage() {
    return percentage;
  }

  public void setPercentage(Integer percentage) {
    this.percentage = percentage;
  }

  public Long getMinDuration() {
    return minDuration;
  }

  public void setMinDuration(Long duration) {
    this.minDuration = this.minDuration == null || duration < this.minDuration ? duration : this.minDuration;
  }

  public void setDuration(Long duration) {
    if(duration == null){
      setMinDuration(0L);
    } else {
      if (this.minDuration == null) {
        setMinDuration(duration);
      } else {
        if (this.minDuration > duration) {
          setMinDuration(duration);
        } else {
          setMaxDuration(duration);
        }
      }
    }
  }

  public Long getMaxDuration() {
    return this.maxDuration == null || this.maxDuration == 0 ? this.minDuration : this.maxDuration;
  }

  public void setMaxDuration(Long duration) {
    this.maxDuration = this.maxDuration == null || duration > this.maxDuration ? duration : this.maxDuration;
  }

  public String getDurationRange() {
    if(this.minDuration == null && this.maxDuration == null){
      this.durationRange = "-";
    }else if(this.minDuration == null){
      this.durationRange = CommonUtil.formatDuration(this.maxDuration);
    }else if(this.maxDuration == null){
      this.durationRange = CommonUtil.formatDuration(this.minDuration);
    }else{
      this.durationRange = String.format("%s - %s", CommonUtil.formatDuration(this.minDuration), CommonUtil.formatDuration(this.maxDuration));
    }
    return this.durationRange;
  }

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

  public void addCount() {
    if (this.count == null) {
      this.count = 0;
    }
    this.count += 1;
  }

  @Override
  public int compareTo(LongestStep o) {
    Long durationA = this.getMaxDuration() == null || this.getMaxDuration() == 0 ?
        this.getMinDuration() :
        this.getMaxDuration();
    Long durationB = o.getMaxDuration() == null || o.getMaxDuration() == 0 ?
        o.getMinDuration() :
        o.getMaxDuration();
    return durationA.compareTo(durationB);
  }
}
