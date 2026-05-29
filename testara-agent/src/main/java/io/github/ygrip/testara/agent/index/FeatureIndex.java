package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;
import java.util.List;

public record FeatureIndex(
    Path path,
    String featureName,
    List<String> tags,
    List<ScenarioIndex> scenarios,
    List<StepIndex> backgroundSteps
) {
  public long totalExampleRows() {
    return scenarios().stream()
        .flatMap(s -> s.examples().stream())
        .mapToLong(ExamplesIndex::rowCount)
        .sum();
  }
}
