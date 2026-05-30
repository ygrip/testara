package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record TestaraProjectProfile(
    Path projectRoot,
    BuildTool buildTool,
    String javaVersion,
    List<String> mavenModules,
    List<Path> featureRoots,
    List<Path> requestSpecRoots,
    List<Path> validationRoots,
    List<FeatureIndex> features,
    List<StepDefinitionIndex> stepDefinitions,
    List<CommandIndex> commands,
    List<ValidationIndex> validations,
    List<DriverIndex> drivers,
    List<TagIndex> tags
) {
  public int totalScenarios() {
    return features().stream().mapToInt(f -> f.scenarios().size()).sum();
  }

  public long totalExampleRows() {
    return features().stream().mapToLong(FeatureIndex::totalExampleRows).sum();
  }

  public long totalSteps() {
    return features().stream()
        .flatMap(f -> f.scenarios().stream())
        .flatMap(s -> s.steps().stream())
        .count();
  }

  public Map<String, Long> tagDistribution() {
    return tags().stream().collect(
        java.util.stream.Collectors.toMap(TagIndex::tag, t -> (long) t.scenarioCount()));
  }
}
