package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.List;

import io.github.ygrip.testara.reporter.support.CommonUtil;

public class Coverage implements Serializable {
  private List<TagCoverage> tagCoverage;
  private String tagTitle;
  private Long duration;
  private String durationText;

  public List<TagCoverage> getTagCoverage() {
    return tagCoverage;
  }

  public void setTagCoverage(List<TagCoverage> tagCoverage) {
    this.tagCoverage = tagCoverage;
  }

  public String getTagTitle() {
    return tagTitle;
  }

  public void setTagTitle(String tagTitle) {
    this.tagTitle = tagTitle;
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
