package io.github.ygrip.testara.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * OpenAI-compatible HTTP client. Works with OpenAI, Azure OpenAI, Ollama, and
 * any other provider that exposes the /v1/chat/completions endpoint.
 */
public class OpenAiLlmClient implements LlmClient {

  private static final Logger LOG = Logger.getLogger(OpenAiLlmClient.class.getName());

  private static final Set<String> SECRET_KEYS = Set.of(
      "password", "secret", "token", "authorization", "api-key", "api_key",
      "client-secret", "client_secret", "private-key", "private_key",
      "cookie", "session");

  private static final Pattern SECRET_PATTERN = Pattern.compile(
      "(?i)(" + String.join("|", SECRET_KEYS) + ")\\s*[=:]\\s*\\S+",
      Pattern.CASE_INSENSITIVE);

  private final LlmConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper mapper;

  public OpenAiLlmClient(LlmConfig config) {
    this.config = config;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    this.mapper = new ObjectMapper();
  }

  @Override
  public boolean isEnabled() {
    return config.hasApiKey();
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    try {
      String body = buildRequestBody(request);
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(config.baseUrl() + "/chat/completions"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.apiKey())
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(
          httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() >= 400) {
        throw new RuntimeException("LLM API returned " + response.statusCode() + ": " + response.body());
      }
      return parseResponse(response.body());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
    }
  }

  private String buildRequestBody(LlmRequest request) throws IOException {
    ObjectNode root = mapper.createObjectNode();
    root.put("model", config.model());
    root.put("temperature", config.temperature());
    root.put("max_tokens", request.maxTokens());

    ArrayNode messages = root.putArray("messages");
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      messages.addObject().put("role", "system").put("content", request.systemPrompt());
    }
    for (LlmMessage msg : request.messages()) {
      messages.addObject()
          .put("role", msg.role())
          .put("content", redact(msg.content()));
    }
    return mapper.writeValueAsString(root);
  }

  private LlmResponse parseResponse(String body) throws IOException {
    JsonNode root = mapper.readTree(body);
    String content = root.path("choices").path(0).path("message").path("content").asText();
    String model = root.path("model").asText(config.model());
    int inputTokens = root.path("usage").path("prompt_tokens").asInt(0);
    int outputTokens = root.path("usage").path("completion_tokens").asInt(0);
    return new LlmResponse(content, model, inputTokens, outputTokens);
  }

  /** Redact secrets from content before sending to the LLM provider. */
  static String redact(String content) {
    if (content == null) return null;
    return SECRET_PATTERN.matcher(content).replaceAll(m -> m.group(1) + "=[REDACTED]");
  }
}
