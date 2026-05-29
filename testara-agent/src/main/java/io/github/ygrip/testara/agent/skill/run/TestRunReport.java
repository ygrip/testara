package io.github.ygrip.testara.agent.skill.run;

import java.util.List;

public record TestRunReport(
    String status,
    long durationMs,
    String tagExpression,
    int total,
    int passed,
    int failed,
    int skipped,
    List<FailedScenario> failedScenarios
) {
  public record FailedScenario(String feature, String scenario, String error) {}

  public String toMarkdown() {
    StringBuilder sb = new StringBuilder();
    sb.append("## Test Run Report\n\n");
    sb.append("**Status:** ").append(status).append("  \n");
    sb.append("**Duration:** ").append(durationMs / 1000).append("s  \n");
    sb.append("**Tag filter:** `").append(tagExpression).append("`  \n\n");
    sb.append("### Summary\n");
    sb.append("- Total: ").append(total).append("\n");
    sb.append("- Passed: ").append(passed).append("\n");
    sb.append("- Failed: ").append(failed).append("\n");
    sb.append("- Skipped: ").append(skipped).append("\n");
    if (!failedScenarios.isEmpty()) {
      sb.append("\n### Failed Scenarios\n");
      int i = 1;
      for (FailedScenario s : failedScenarios) {
        sb.append(i++).append(". **").append(s.scenario()).append("**\n");
        sb.append("   Reason: ").append(s.error()).append("\n");
        sb.append("   Feature: `").append(s.feature()).append("`\n\n");
      }
    }
    return sb.toString();
  }
}
