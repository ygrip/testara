package io.github.ygrip.testara.agent.skill;

import java.util.List;
import java.util.Map;

public record AgentResult(
    String summary,
    List<String> warnings,
    List<FilePatch> patches,
    Map<String, Object> metadata
) {
  public static AgentResult of(String summary) {
    return new AgentResult(summary, List.of(), List.of(), Map.of());
  }
}
