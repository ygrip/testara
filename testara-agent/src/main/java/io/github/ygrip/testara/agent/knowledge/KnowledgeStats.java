package io.github.ygrip.testara.agent.knowledge;

public record KnowledgeStats(
    int featureCount,
    int scenarioCount,
    int stepDefCount,
    int commandCount,
    int validationCount,
    int tagCount,
    int trackedFiles,
    KnowledgeStatus status
) {
  public static KnowledgeStats from(int features, int scenarios, int stepDefs,
      int commands, int validations, int tags, int files, KnowledgeStatus status) {
    return new KnowledgeStats(features, scenarios, stepDefs, commands, validations, tags, files, status);
  }
}
