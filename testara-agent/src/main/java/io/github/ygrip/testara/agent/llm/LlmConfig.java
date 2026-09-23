package io.github.ygrip.testara.agent.llm;

import java.util.Map;
public record LlmConfig(
    String provider,
    String model,
    String apiKey,
    String baseUrl,
    double temperature,
    int maxContextFiles,
    int maxOutputFiles,
    boolean applyEnabled
) {
  public static final String DEFAULT_PROVIDER = "openai";
  public static final String DEFAULT_MODEL = "gpt-4.1-mini";
  public static final double DEFAULT_TEMPERATURE = 0.2;

  public static LlmConfig fromEnv() {
    return fromEnv(Map.of());
  }

  public static LlmConfig fromEnv(Map<String, String> options) {
    String provider = env("TESTARA_AGENT_PROVIDER",
        options.getOrDefault("llm.provider", DEFAULT_PROVIDER));
    String defaultBaseUrl = ("local".equalsIgnoreCase(provider) || "ollama".equalsIgnoreCase(provider))
        ? "http://localhost:11434" : "https://api.openai.com/v1";
    return new LlmConfig(
        provider,
        env("TESTARA_AGENT_MODEL", options.getOrDefault("llm.model", DEFAULT_MODEL)),
        env("TESTARA_AGENT_API_KEY", options.get("llm.apiKey")),
        env("TESTARA_AGENT_BASE_URL", options.getOrDefault("llm.baseUrl", defaultBaseUrl)),
        doubleValue(env("TESTARA_AGENT_TEMPERATURE",
            options.getOrDefault("llm.temperature", String.valueOf(DEFAULT_TEMPERATURE))), DEFAULT_TEMPERATURE),
        intValue(env("TESTARA_AGENT_MAX_CONTEXT_FILES",
            options.getOrDefault("llm.maxContextFiles", "80")), 80),
        intValue(env("TESTARA_AGENT_MAX_OUTPUT_FILES",
            options.getOrDefault("llm.maxOutputFiles", "20")), 20),
        Boolean.parseBoolean(env("TESTARA_AGENT_APPLY_ENABLED",
            options.getOrDefault("llm.applyEnabled", "false")))
    );
  }

  private static int intValue(String value, int fallback) {
    try { return Integer.parseInt(value); }
    catch (NumberFormatException e) { return fallback; }
  }

  private static double doubleValue(String value, double fallback) {
    try { return Double.parseDouble(value); }
    catch (NumberFormatException e) { return fallback; }
  }

  private static String env(String key, String defaultValue) {
    String val = System.getenv(key);
    return (val != null && !val.isBlank()) ? val : defaultValue;
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
