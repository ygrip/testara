package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.github.ygrip.testara.reporter.parser.ElementDeserializer;
import io.github.ygrip.testara.reporter.parser.TagsDeserializer;
import io.github.ygrip.testara.reporter.support.CommonUtil;

public class Feature implements Reportable, Serializable, Durationable, Comparable<Feature> {
  @JsonProperty("uri")
  private final String uri = null;
  @JsonProperty("description")
  private final String description = null;
  @JsonProperty("keyword")
  private final String keyword = null;
  @JsonProperty("line")
  private final Integer line = null;
  @JsonProperty("tags")
  @JsonDeserialize(using = TagsDeserializer.class)
  private final List<Tag> tags = new ArrayList<>();
  @JsonIgnore
  private final StatusCounter elementsCounter = new StatusCounter();
  @JsonIgnore
  private final StatusCounter stepsCounter = new StatusCounter();
  @JsonProperty("id")
  private String id = null;
  @JsonProperty("name")
  private String name = null;
  @JsonProperty("elements")
  @JsonDeserialize(using = ElementDeserializer.class)
  private List<Element> elements = new ArrayList<>();
  @JsonIgnore
  private Status featureStatus;
  @JsonIgnore
  private long duration;
  @JsonIgnore
  private boolean calculated;

  public Feature() {
    this.calculated = false;
  }

  @JsonIgnore
  public LocalDateTime getStartTime() {
    Optional<Element> firstScenario = getElements().stream()
        .filter(element -> element.getStartTime() != null)
        .min(Comparator.comparing(Element::getStartTime));
    return firstScenario.map(Element::getStartTime).orElse(null);
  }

  @JsonIgnore
  public LocalDateTime getStopTime() {
    Optional<Element> lastScenario = getElements().stream()
        .filter(element -> element.getStopTime() != null)
        .max(Comparator.comparing(Element::getStopTime));
    return lastScenario.map(Element::getStopTime).orElse(null);
  }

  public void setFeatureStatus(Status featureStatus) {
    this.featureStatus = featureStatus;
  }

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUri() {
    return this.uri;
  }

  public void addElements(List<Element> newElements) {
    this.elements.addAll(newElements);
  }

  public List<Element> getElements() {
    return this.elements;
  }

  public void setElements(List<Element> newElements) {
    this.elements = newElements;
  }

  public List<Tag> getTags() {
    return this.tags;
  }

  @JsonIgnore
  public Status getStatus() {
    if (this.featureStatus == null) {
      this.featureStatus = calculateFeatureStatus();
    }
    return this.featureStatus;
  }

  public String getName() {
    return this.name == null ? "" : name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getKeyword() {
    return this.keyword == null ? "" : this.keyword;
  }

  public Integer getLine() {
    return this.line;
  }

  public String getDescription() {
    return this.description == null ? "" : this.description;
  }

  @JsonIgnore
  public int getFeatures() {
    return 1;
  }

  @JsonIgnore
  public int getPassedFeatures() {
    return this.getStatus().isPassed() ? 1 : 0;
  }

  @JsonIgnore
  public int getFailedFeatures() {
    return this.getStatus().isPassed() ? 0 : 1;
  }

  @JsonIgnore
  public int getScenarios() {
    return this.elements == null || this.elements.isEmpty() ?
        0 :
        this.elements.stream().filter(Element::isScenario).collect(Collectors.toList()).size();
  }

  @JsonIgnore
  public int getSteps() {
    if (stepsCounter.getFinalStatus() == null) {
      calculateSteps();
    }
    return this.stepsCounter.size();
  }

  @JsonIgnore
  public int getPassedSteps() {
    if (stepsCounter.getFinalStatus() == null) {
      calculateSteps();
    }
    return this.stepsCounter.getValueFor(Status.PASSED);
  }

  @JsonIgnore
  public int getFailedSteps() {
    if (stepsCounter.getFinalStatus() == null) {
      calculateSteps();
    }
    return this.stepsCounter.getValueFor(Status.FAILED);
  }

  @JsonIgnore
  public int getPendingSteps() {
    if (stepsCounter.getFinalStatus() == null) {
      calculateSteps();
    }
    return this.stepsCounter.getValueFor(Status.PENDING);
  }

  @JsonIgnore
  public int getSkippedSteps() {
    return this.stepsCounter.getValueFor(Status.SKIPPED);
  }

  @JsonIgnore
  public int getUndefinedSteps() {
    return this.stepsCounter.getValueFor(Status.UNDEFINED);
  }

  @JsonIgnore
  public long getDuration() {
    if (duration == 0) {
      calculateSteps();
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
  public int getPassedScenarios() {
    calculateSteps();
    return this.elementsCounter.getValueFor(Status.PASSED);
  }

  @JsonIgnore
  public int getFailedScenarios() {
    calculateSteps();
    return this.elementsCounter.getValueFor(Status.FAILED);
  }

  @JsonIgnore
  public int getSkippedScenarios() {
    calculateSteps();
    return this.elementsCounter.getValueFor(Status.SKIPPED);
  }

  @JsonIgnore
  public int getUndefinedScenarios() {
    calculateSteps();
    return this.elementsCounter.getValueFor(Status.UNDEFINED);
  }

  @JsonIgnore
  public int getPendingScenarios() {
    calculateSteps();
    return this.elementsCounter.getValueFor(Status.PENDING);
  }

  @JsonIgnore
  private Status calculateFeatureStatus() {
    StatusCounter statusCounter = new StatusCounter();

    for (Element element : this.elements) {
      statusCounter.incrementFor(element.getStatus());
    }

    return statusCounter.getFinalStatus();
  }

  private void calculateSteps() {
    if (!calculated) {
      for (Element element : this.elements) {
        List<Step> steps = element.getSteps();
        int undefined = 0;
        int pending = 0;
        int skipped = 0;

        for (Step step : steps) {
          Status status = step.getResult().getStatus();
          switch (status) {
            case UNDEFINED:
              undefined++;
              break;
            case PENDING:
              pending++;
              break;
            case SKIPPED:
              skipped++;
              break;
            default:
              break;
          }
          this.stepsCounter.incrementFor(step.getResult().getStatus());
          this.duration += step.getDuration();
        }

        if (element.isScenario() || element.isScenarioOutline()) {
          Status status = element.getStatus();
          if (status.equals(Status.PASSED)) {
            this.elementsCounter.incrementFor(Status.PASSED);
          } else if (status.equals(Status.FAILED)) {
            this.elementsCounter.incrementFor(Status.FAILED);
          } else {
            if ((double) (skipped / steps.size()) > 0.5) {
              this.elementsCounter.incrementFor(Status.SKIPPED);
            } else if ((double) (pending / steps.size()) > 0.5) {
              this.elementsCounter.incrementFor(Status.PENDING);
            } else if ((double) (undefined / steps.size()) > 0.5) {
              this.elementsCounter.incrementFor(Status.UNDEFINED);
            }
          }
        }
      }
    }
    calculated = true;
  }

  @Override
  @JsonIgnore
  public int compareTo(Feature other) {
    if(other == null){
      return 1;
    }
    return Integer.compare(getLine(), other.getLine());
  }
}
