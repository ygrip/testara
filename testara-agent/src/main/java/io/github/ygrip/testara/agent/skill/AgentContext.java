package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.llm.LlmClient;

import java.nio.file.Path;
import java.util.Map;

public record AgentContext(
    Path projectRoot,
    TestaraProjectProfile profile,
    AgentMode mode,
    LlmClient llmClient,
    Map<String, String> options
) {
  public boolean allowsWrite() {
    return mode == AgentMode.APPLY;
  }

  public boolean allowsExecution() {
    return mode == AgentMode.APPLY;
  }
}
