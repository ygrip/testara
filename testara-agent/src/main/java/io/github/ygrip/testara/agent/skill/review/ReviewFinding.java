package io.github.ygrip.testara.agent.skill.review;

import java.nio.file.Path;

public record ReviewFinding(
    ReviewSeverity severity,
    String title,
    String description,
    Path featurePath,
    String scenarioName,
    String suggestion
) {
  public String toMarkdown() {
    return String.format("**[%s]** %s\n> %s\n%s%s\n",
        severity, title, description,
        featurePath != null ? "_" + featurePath.getFileName() + "_ " : "",
        scenarioName != null && !scenarioName.isBlank() ? "— `" + scenarioName + "`" : "");
  }
}
