package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;
import java.util.List;

public record TagIndex(
    String tag,
    int featureCount,
    int scenarioCount,
    int executableCaseCount,
    List<Path> featurePaths,
    List<String> scenarioNames
) {
  public TagIndex(String tag, int featureCount, int scenarioCount,
      List<Path> featurePaths, List<String> scenarioNames) {
    this(tag, featureCount, scenarioCount, scenarioCount, featurePaths, scenarioNames);
  }
}
