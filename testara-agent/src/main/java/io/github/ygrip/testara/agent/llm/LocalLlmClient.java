package io.github.ygrip.testara.agent.llm;

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
import java.util.logging.Logger;

/**
 * LLM client for local models via Ollama API (http://localhost:11434).
 *
 * <p>Supports any Ollama-compatible endpoint. Default model is configurable
 * via {@code TESTARA_AGENT_MODEL}. No API key is required for local models.
 */
public class LocalLlmClient implements LlmClient {

  private static final Logger LOG = Logger.getLogger(LocalLlmClient.class.getName());
  private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";

  private final String baseUrl;
  private final String model;
  private final double temperature;
  private final HttpClient httpClient;
  private final ObjectMapper mapper;

  public LocalLlmClient(LlmConfig config) {
    this.baseUrl = (config.baseUrl() != null && !config.baseUrl().isBlank())
        ? config.baseUrl() : DEFAULT_OLLAMA_URL;
    this.model = config.model();
    this.temperature = config.temperature();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    this.mapper = new ObjectMapper();
  }

  @Override
  public boolean isEnabled() {
    return true; // local models are always "enabled" if configured
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    try {
      String body = buildRequestBody(request);
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/generate"))
          .timeout(Duration.ofMinutes(5))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(
          httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() >= 400) {
        throw new RuntimeException("Ollama API returned " + response.statusCode()
            + ": " + response.body());
      }
      return parseResponse(response.body(), request);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new RuntimeException("Local LLM request failed: " + e.getMessage(), e);
    }
  }

  private String buildRequestBody(LlmRequest request) throws IOException {
    ObjectNode root = mapper.createObjectNode();
    root.put("model", model);
    root.put("stream", false);

    ObjectNode options = root.putObject("options");
    options.put("temperature", temperature);

    // Build prompt from messages
    StringBuilder prompt = new StringBuilder();
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      prompt.append(request.systemPrompt()).append("\n\n");
    }
    for (LlmMessage msg : request.messages()) {
      prompt.append(msg.role()).append(": ").append(msg.content()).append("\n");
    }
    root.put("prompt", prompt.toString());

    return mapper.writeValueAsString(root);
  }

  private LlmResponse parseResponse(String body, LlmRequest request) throws IOException {
    var node = mapper.readTree(body);
    String content = node.path("response").asText("");
    int evalCount = node.path("eval_count").asInt(0);
    int promptEvalCount = node.path("prompt_eval_count").asInt(0);
    return new LlmResponse(content, model, promptEvalCount, evalCount);
  }
}
