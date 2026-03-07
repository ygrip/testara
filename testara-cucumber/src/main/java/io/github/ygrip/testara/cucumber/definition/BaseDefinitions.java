package io.github.ygrip.testara.cucumber.definition;

import io.cucumber.java.ParameterType;

import java.util.concurrent.TimeUnit;

public class BaseDefinitions {

  @ParameterType("milliseconds|seconds|minutes|hours")
  public TimeUnit timeUnit(String state) {
    return TimeUnit.valueOf(state.trim().toUpperCase());
  }

  @ParameterType("true|false")
  public Boolean bool(String state) {
    return Boolean.parseBoolean(state);
  }

  @ParameterType("should|should not")
  public String shouldOrShouldNot(String state) {
    return state;
  }

  @ParameterType("set|define")
  public String setOrDefine(String action) {
    return action;
  }

  @ParameterType("to|=")
  public String setTo(String action) {
    return action;
  }

  @ParameterType("greater|less")
  public String greaterOrLess(String state) {
    return state;
  }

  @ParameterType("than|than equal")
  public String thanOrEqual(String state) {
    return state;
  }

  @ParameterType("ascending|descending")
  public String ascendingOrDescending(String state) {
    return state;
  }

  @ParameterType("previous|next")
  public String previousOrNext(String state) {
    return state;
  }

  @ParameterType(
      "contains|contains ignore case|equal|equal ignore case|matches|starts with|starts with ignore case|ends with|ends with ignore case")
  public String stringValidation(String state) {
    return state;
  }

  @ParameterType("request|response|all")
  public String requestOrResponse(String state) {
    return state;
  }

  @ParameterType("\\[mongo\\]")
  public String mongo(String state) {
    return state;
  }

  @ParameterType("\\[sql\\]")
  public String sql(String state) {
    return state;
  }

  @ParameterType("\\[elastic-search\\]")
  public String elasticsearch(String state) {
    return state;
  }

  @ParameterType("\\[file\\]")
  public String file(String state) {
    return state;
  }

  @ParameterType("desktop|mobile|android|ios")
  public String devices(String state) {
    return state;
  }

  @ParameterType("standalone|embedded|mitmproxy")
  public String standAloneOrEmbedded(String state) {
    return state;
  }

  @ParameterType("displayed|not displayed")
  public String displayedOrNotDisplayed(String state) {
    return state;
  }

  @ParameterType("clickable|not clickable")
  public String clickableOrNotClickable(String state) {
    return state;
  }

  @ParameterType("enabled|visible|disabled|not visible|clickable|present|not present|not clickable|selected")
  public String elementState(String state) {
    return state;
  }

  @ParameterType("downloaded|not downloaded")
  public String downloadState(String state) {
    return state;
  }
}
