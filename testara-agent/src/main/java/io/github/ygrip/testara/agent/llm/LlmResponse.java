package io.github.ygrip.testara.agent.llm;

public record LlmResponse(String content, String model, int inputTokens, int outputTokens) {}
