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
    return toMarkdown(false);
  }

  /** Token-efficient concise output for AI assistants. */
  public String toConcise() {
    StringBuilder sb = new StringBuilder();
    sb.append(status).append(" | ").append(durationMs / 1000).append("s | ")
        .append(tagExpression).append(" | ")
        .append(passed).append("/").append(failed).append("/").append(skipped);
    if (!failedScenarios.isEmpty()) {
      sb.append("\n");
      failedScenarios.forEach(s ->
          sb.append("  FAIL: ").append(s.scenario()).append(" — ").append(s.error()).append("\n"));
    }
    return sb.toString();
  }

  public String toMarkdown(boolean concise) {
    if (concise) return toConcise();
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

  public String toJson() {
    StringBuilder sb = new StringBuilder("{\n");
    sb.append("  \"status\": \"").append(status).append("\",\n");
    sb.append("  \"durationMs\": ").append(durationMs).append(",\n");
    sb.append("  \"tagExpression\": \"").append(tagExpression.replace("\"", "\\\"")).append("\",\n");
    sb.append("  \"total\": ").append(total).append(",\n");
    sb.append("  \"passed\": ").append(passed).append(",\n");
    sb.append("  \"failed\": ").append(failed).append(",\n");
    sb.append("  \"skipped\": ").append(skipped).append(",\n");
    sb.append("  \"failedScenarios\": [");
    for (int i = 0; i < failedScenarios.size(); i++) {
      FailedScenario s = failedScenarios.get(i);
      if (i > 0) sb.append(",");
      sb.append("\n    {\"feature\": \"").append(s.feature().replace("\"", "\\\""))
          .append("\", \"scenario\": \"").append(s.scenario().replace("\"", "\\\""))
          .append("\", \"error\": \"").append(s.error().replace("\"", "\\\"")).append("\"}");
    }
    sb.append(failedScenarios.isEmpty() ? "]\n}" : "\n  ]\n}");
    return sb.toString();
  }

}
