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
  /** Default for {@code local}/{@code ollama}: an OpenAI model name does not exist on an Ollama server. */
  public static final String DEFAULT_LOCAL_MODEL = "llama3.1";
  public static final double DEFAULT_TEMPERATURE = 0.2;
  static final String API_KEY_ENV = "TESTARA_AGENT_API_KEY";
  static final String BASE_URL_ENV = "TESTARA_AGENT_BASE_URL";

  public static LlmConfig fromEnv() {
    return fromEnv(Map.of());
  }

  /**
   * Builds the config from {@code TESTARA_AGENT_*} environment variables, falling back to
   * {@code llm.*} options (e.g. from a checked-in testara-agent.yaml).
   */
  public static LlmConfig fromEnv(Map<String, String> options) {
    return from(System.getenv(), options);
  }

  static LlmConfig from(Map<String, String> environment, Map<String, String> options) {
    String provider = env(environment, "TESTARA_AGENT_PROVIDER",
        options.getOrDefault("llm.provider", DEFAULT_PROVIDER));
    boolean local = "local".equalsIgnoreCase(provider) || "ollama".equalsIgnoreCase(provider);
    String defaultBaseUrl = "https://api.openai.com/v1";
    String defaultModel = DEFAULT_MODEL;
    if (local) {
      defaultBaseUrl = "http://localhost:11434";
      defaultModel = DEFAULT_LOCAL_MODEL;
    }
    // A key from the environment must never be sent to a host chosen by repository config:
    // then only TESTARA_AGENT_BASE_URL (or the provider default) may set the endpoint.
    String baseUrlFallback = options.getOrDefault("llm.baseUrl", defaultBaseUrl);
    if (hasText(environment.get(API_KEY_ENV))) baseUrlFallback = defaultBaseUrl;
    return new LlmConfig(
        provider,
        env(environment, "TESTARA_AGENT_MODEL", options.getOrDefault("llm.model", defaultModel)),
        env(environment, API_KEY_ENV, options.get("llm.apiKey")),
        env(environment, BASE_URL_ENV, baseUrlFallback),
        doubleValue(env(environment, "TESTARA_AGENT_TEMPERATURE",
            options.getOrDefault("llm.temperature", String.valueOf(DEFAULT_TEMPERATURE))), DEFAULT_TEMPERATURE),
        intValue(env(environment, "TESTARA_AGENT_MAX_CONTEXT_FILES",
            options.getOrDefault("llm.maxContextFiles", "80")), 80),
        intValue(env(environment, "TESTARA_AGENT_MAX_OUTPUT_FILES",
            options.getOrDefault("llm.maxOutputFiles", "20")), 20),
        Boolean.parseBoolean(env(environment, "TESTARA_AGENT_APPLY_ENABLED",
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

  private static String env(Map<String, String> environment, String key, String defaultValue) {
    String val = environment.get(key);
    if (hasText(val)) return val;
    return defaultValue;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
