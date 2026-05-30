package io.github.ygrip.testara.agent.knowledge;

public record ProjectOverviewStats(
    int featureFiles,
    int scenarios,
    int scenarioOutlines,
    long exampleRows,
    long totalSteps,
    int stepDefinitions,
    int customCommands,
    int customValidators,
    int tagCount,
    double avgStepsPerScenario
) {
  public static ProjectOverviewStats empty() {
    return new ProjectOverviewStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);
  }
}
