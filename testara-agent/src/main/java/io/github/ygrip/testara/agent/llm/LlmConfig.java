package io.github.ygrip.testara.agent.llm;

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
    return new LlmConfig(
        env("TESTARA_AGENT_PROVIDER", DEFAULT_PROVIDER),
        env("TESTARA_AGENT_MODEL", DEFAULT_MODEL),
        env("TESTARA_AGENT_API_KEY", null),
        env("TESTARA_AGENT_BASE_URL", "https://api.openai.com/v1"),
        Double.parseDouble(env("TESTARA_AGENT_TEMPERATURE", String.valueOf(DEFAULT_TEMPERATURE))),
        Integer.parseInt(env("TESTARA_AGENT_MAX_CONTEXT_FILES", "80")),
        Integer.parseInt(env("TESTARA_AGENT_MAX_OUTPUT_FILES", "20")),
        Boolean.parseBoolean(env("TESTARA_AGENT_APPLY_ENABLED", "false"))
    );
  }

  private static String env(String key, String defaultValue) {
    String val = System.getenv(key);
    return (val != null && !val.isBlank()) ? val : defaultValue;
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
