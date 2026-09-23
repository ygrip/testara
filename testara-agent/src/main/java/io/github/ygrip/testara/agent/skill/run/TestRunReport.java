package io.github.ygrip.testara.agent.skill.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
  private static final ObjectMapper MAPPER = new ObjectMapper();

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
    try {
      return MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize test run report", e);
    }
  }
}
