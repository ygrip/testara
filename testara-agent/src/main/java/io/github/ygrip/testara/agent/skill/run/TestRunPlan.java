package io.github.ygrip.testara.agent.skill.run;

import java.util.List;

public record TestRunPlan(
    String intent,
    String tagExpression,
    int matchedFeatures,
    int matchedScenarios,
    String mavenCommand,
    List<String> matchedScenarioNames
) {
  public String toMarkdown() {
    String matchedNames = matchedScenarioNames.isEmpty() ? ""
        : "\n**Matched scenario examples:** " + String.join(" | ", matchedScenarioNames) + "\n";
    return String.format("""
        ## Test Run Plan

        **Intent:** %s
        **Resolved tag expression:** `%s`
        **Matched features:** %d
        **Matched scenarios:** %d
        %s
        **Command:**
        ```
        %s
        ```
        """, intent, tagExpression, matchedFeatures, matchedScenarios, matchedNames, mavenCommand);
  }
}
