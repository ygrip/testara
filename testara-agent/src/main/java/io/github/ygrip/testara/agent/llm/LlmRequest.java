package io.github.ygrip.testara.agent.llm;

import java.util.List;

public record LlmRequest(
    String systemPrompt,
    List<LlmMessage> messages,
    double temperature,
    int maxTokens
) {
  public static LlmRequest of(String system, String userMessage) {
    return new LlmRequest(system, List.of(new LlmMessage("user", userMessage)), 0.2, 4096);
  }
}
