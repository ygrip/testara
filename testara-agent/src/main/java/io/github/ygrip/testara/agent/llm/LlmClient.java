package io.github.ygrip.testara.agent.llm;

public interface LlmClient {
  LlmResponse complete(LlmRequest request);
  boolean isEnabled();
}
