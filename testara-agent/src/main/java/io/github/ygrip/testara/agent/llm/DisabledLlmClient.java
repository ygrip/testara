package io.github.ygrip.testara.agent.llm;

/** No-op client used when no LLM provider is configured or for read-only skills. */
public class DisabledLlmClient implements LlmClient {

  @Override
  public LlmResponse complete(LlmRequest request) {
    throw new IllegalStateException(
        "LLM client is disabled. Set TESTARA_AGENT_API_KEY to enable LLM features.");
  }

  @Override
  public boolean isEnabled() {
    return false;
  }
}
