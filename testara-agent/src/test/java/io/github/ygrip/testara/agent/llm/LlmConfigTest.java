package io.github.ygrip.testara.agent.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmConfigTest {

  @Test
  void repositoryBaseUrlIsIgnoredWhenTheKeyComesFromTheEnvironment() {
    LlmConfig config = LlmConfig.from(Map.of("TESTARA_AGENT_API_KEY", "sk-env"),
        Map.of("llm.baseUrl", "https://attacker.example/v1"));

    assertEquals("https://api.openai.com/v1", config.baseUrl());
    assertEquals("sk-env", config.apiKey());
  }

  @Test
  void environmentBaseUrlStillApplies() {
    LlmConfig config = LlmConfig.from(
        Map.of("TESTARA_AGENT_API_KEY", "sk-env", "TESTARA_AGENT_BASE_URL", "https://gateway.internal/v1"),
        Map.of("llm.baseUrl", "https://attacker.example/v1"));

    assertEquals("https://gateway.internal/v1", config.baseUrl());
  }

  @Test
  void repositoryBaseUrlAppliesWithoutAnEnvironmentKey() {
    LlmConfig config = LlmConfig.from(Map.of(), Map.of("llm.baseUrl", "https://proxy.example/v1"));

    assertEquals("https://proxy.example/v1", config.baseUrl());
  }

  @Test
  void localProvidersDefaultToAnOllamaModelAndEndpoint() {
    LlmConfig config = LlmConfig.from(Map.of("TESTARA_AGENT_PROVIDER", "ollama"), Map.of());

    assertEquals(LlmConfig.DEFAULT_LOCAL_MODEL, config.model());
    assertEquals("http://localhost:11434", config.baseUrl());
    assertEquals(LlmConfig.DEFAULT_MODEL, LlmConfig.from(Map.of(), Map.of()).model());
  }
}
