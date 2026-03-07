package io.github.ygrip.testara.reporter.cucumber;


public interface Reportable {
  String getName();

  int getFeatures();

  int getPassedFeatures();

  int getFailedFeatures();

  int getScenarios();

  int getPassedScenarios();

  int getFailedScenarios();

  int getSteps();

  int getPassedSteps();

  int getFailedSteps();

  int getSkippedSteps();

  int getUndefinedSteps();

  int getPendingSteps();

  long getDuration();

  String getFormattedDuration();

  Status getStatus();
}
