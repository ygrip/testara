package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.github.ygrip.testara.reporter.parser.TagsDeserializer;
import io.github.ygrip.testara.reporter.support.CommonUtil;

public class Element implements Serializable, Durationable, Comparable<Element> {
  @JsonIgnore
  private static final String SCENARIO_TYPE = "scenario";
  @JsonIgnore
  private static final String BACKGROUND_TYPE = "background";
  @JsonProperty("type")
  private final String type = null;
  @JsonProperty("description")
  private final String description = null;
  @JsonProperty("keyword")
  private final String keyword = null;
  @JsonProperty("line")
  private final Integer line = null;
  @JsonProperty("start_timestamp")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private final LocalDateTime startTime = null;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Hook> before = new ArrayList<>();
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Hook> after = new ArrayList<>();
  @JsonProperty("tags")
  @JsonDeserialize(using = TagsDeserializer.class)
  private final List<Tag> tags = new ArrayList<>();
  @JsonProperty("steps")
  private final List<Step> steps = new ArrayList<>();
  @JsonProperty("id")
  private String id;
  @JsonProperty("name")
  private String name;
  @JsonIgnore
  private Status elementStatus;
  @JsonIgnore
  private Status beforeStatus;
  @JsonIgnore
  private Status afterStatus;
  @JsonIgnore
  private Status stepsStatus;
  @JsonIgnore
  private Feature feature;
  @JsonIgnore
  private long duration;
  @JsonIgnore
  private int index;

  public Element() {
  }

  public int getIndex() {
    return this.index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  @JsonIgnore
  public LocalDateTime getStopTime() {
    if (getStartTime() != null) {
      return getStartTime().plusNanos(getDuration());
    } else {
      return null;
    }
  }

  public void setElementStatus(Status elementStatus) {
    this.elementStatus = elementStatus;
  }

  public List<Step> getSteps() {
    return this.steps;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Hook> getBefore() {
    return this.before;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Hook> getAfter() {
    return this.after;
  }

  public List<Tag> getTags() {
    return this.tags;
  }

  @JsonIgnore
  public Status getStatus() {
    if (this.elementStatus == null) {
      this.elementStatus = calculateElementStatus();
    }
    return this.elementStatus;
  }

  @JsonIgnore
  public Status getBeforeStatus() {
    return this.beforeStatus;
  }

  public void setBeforeStatus(Status beforeStatus) {
    this.beforeStatus = beforeStatus;
  }

  @JsonIgnore
  public Status getAfterStatus() {
    return this.afterStatus;
  }

  public void setAfterStatus(Status afterStatus) {
    this.afterStatus = afterStatus;
  }

  @JsonIgnore
  public Status getStepsStatus() {
    return this.stepsStatus;
  }

  public void setStepsStatus(Status stepsStatus) {
    this.stepsStatus = stepsStatus;
  }

  public String getId() {
    if (isBackground()) {
      return "background-" + getIndex();
    }
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getKeyword() {
    return this.keyword;
  }

  @JsonProperty("start_timestamp")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  public LocalDateTime getStartTime() {
    return this.startTime;
  }

  public Integer getLine() {
    return this.line;
  }

  public String getType() {
    return this.type;
  }

  public String getDescription() {
    return this.description == null ? "" : this.description;
  }

  @JsonIgnore
  public boolean isScenario() {
    return "scenario".equalsIgnoreCase(this.type);
  }

  @JsonIgnore
  public boolean isBackground() {
    return "background".equalsIgnoreCase(this.type);
  }

  @JsonIgnore
  public boolean isScenarioOutline() {
    final List<String> OUTLINES = Arrays.asList("scenario outline", "scenario template");
    if (getKeyword() == null) {
      return isScenario();
    } else {
      return isScenario() && OUTLINES.contains(getKeyword().toLowerCase().trim());
    }
  }

  @JsonIgnore
  public Feature getFeature() {
    return this.feature;
  }

  public void setFeature(Feature feature) {
    this.feature = feature;
  }

  @JsonIgnore
  public long getDuration() {
    if (duration == 0) {
      calculateDuration();
    }
    return this.duration;
  }

  public void setDuration(long duration) {
    this.duration = duration;
  }

  @JsonIgnore
  public String getFormattedDuration() {
    return CommonUtil.formatDuration(this.duration);
  }

  @JsonIgnore
  private Status calculateElementStatus() {
    setMetaData();
    StatusCounter statusCounter = new StatusCounter();
    statusCounter.incrementFor(this.stepsStatus);
    statusCounter.incrementFor(this.beforeStatus);
    statusCounter.incrementFor(this.afterStatus);
    return statusCounter.getFinalStatus();
  }

  public void setMetaData() {
    StatusCounter stepCounter = new StatusCounter();
    for (Step step : this.steps) {
      step.setMetaData();
      stepCounter.incrementFor(step.getResult().getStatus());
      stepCounter.incrementFor(step.getBeforeStatus());
      stepCounter.incrementFor(step.getAfterStatus());
    }
    this.stepsStatus = stepCounter.getFinalStatus();
    this.beforeStatus = (new StatusCounter(this.before.toArray(new Resultsable[0]))).getFinalStatus();
    this.afterStatus = (new StatusCounter(this.after.toArray(new Resultsable[0]))).getFinalStatus();
  }

  private void calculateDuration() {
    for (Hook hook : getBefore()) {
      this.duration += hook.getResult().getDuration();
    }
    for (Step step : this.steps) {
      for (Hook hook : step.getBefore()) {
        this.duration += hook.getResult().getDuration();
      }
      this.duration += step.getResult().getDuration();
      for (Hook hook : step.getAfter()) {
        this.duration += hook.getResult().getDuration();
      }
    }
    for (Hook hook : getAfter()) {
      this.duration += hook.getResult().getDuration();
    }
  }

  @Override
  @JsonIgnore
  public int compareTo(Element otherElement) {
    if (otherElement == null) {
      return 1;
    }
    if (isBackground() && otherElement.isBackground()) {
      return Integer.compare(getLine(), otherElement.getLine());
    } else if (!isBackground() && !otherElement.isBackground()) {
      return getStartTime().compareTo(otherElement.getStartTime());
    } else {
      if (Objects.equals(getId(), otherElement.getId())) {
        return 0;
      } else {
        return Integer.compare(getLine(), otherElement.getLine());
      }
    }
  }
}
