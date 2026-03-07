package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.ygrip.testara.reporter.support.CommonUtil;

public class Result implements Durationable, Serializable {
  @JsonProperty("duration")
  private final Long duration;
  @JsonProperty("status")
  private Status status;
  @JsonProperty("error_message")
  private String errorMessage;

  public Result() {
    this.status = Status.UNDEFINED;
    this.errorMessage = null;
    this.duration = 0L;
  }

  public Status getStatus() {
    return this.status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public long getDuration() {
    return this.duration;
  }

  @JsonIgnore
  public String getFormattedDuration() {
    return CommonUtil.formatDuration(this.duration);
  }

  @JsonProperty("error_message")
  public String getErrorMessage() {
    return this.errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @JsonIgnore
  public final String getErrorMessageTitle() {
    if (this.errorMessage != null) {
      String[] title = this.errorMessage.split("[\\p{Space}]+");
      if (title.length > 0) {
        return title[0].replaceAll(":", "");
      }
    }

    return "";
  }
}
