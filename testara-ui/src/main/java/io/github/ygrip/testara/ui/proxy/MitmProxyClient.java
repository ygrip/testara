package io.github.ygrip.testara.ui.proxy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.ui.model.MitmProxyCreateInstanceResponse;
import io.github.ygrip.testara.ui.model.MitmProxyHealthResponse;
import io.github.ygrip.testara.ui.model.MitmProxyInstanceDetail;
import io.github.ygrip.testara.ui.model.MitmProxyInstanceSummary;
import io.github.ygrip.testara.ui.model.MitmProxyMessageResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRenewResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRule;
import io.github.ygrip.testara.ui.model.MitmProxyRuleResponse;

import lombok.extern.log4j.Log4j2;

/**
 * HTTP client for the MitmProxy Grid REST API (v2).
 * <p>
 * All operations are instance-scoped: each mitmproxy instance has its own port,
 * CA certificate, and independent set of interception rules.
 * <p>
 * Thread-safe and stateless — safe to share across tests.
 *
 * @see <a href="openapi.json">MitmProxy Grid OpenAPI spec</a>
 */
@Log4j2
public class MitmProxyClient {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_RETRIES = 3;
  private static final long RETRY_BASE_MS = 500;

  private final String baseUrl;
  private final HttpClient client;

  public MitmProxyClient(String apiBaseUrl) {
    this(apiBaseUrl, HttpClient.newBuilder()
        .connectTimeout(DEFAULT_TIMEOUT)
        .build());
  }

  public MitmProxyClient(String apiBaseUrl, HttpClient client) {
    this.client = client;
    this.baseUrl = normalizeUrl(apiBaseUrl);
  }

  // ── Health ────────────────────────────────────────────────────────
  // GET /health

  public MitmProxyHealthResponse health() throws Exception {
    String response = sendWithRetry("GET", "health", null);
    return MapperHelper.toObject(response, MitmProxyHealthResponse.class);
  }

  public boolean isReady() {
    try {
      MitmProxyHealthResponse status = health();
      return status != null && status.isHealthy();
    } catch (Exception e) {
      log.trace("MitmProxy health check failed: {}", e.getMessage());
      return false;
    }
  }

  public boolean waitUntilReady(long timeoutMs, long pollMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (isReady()) {
        return true;
      }
      try {
        Thread.sleep(pollMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return isReady();
  }

  // ── Instances ─────────────────────────────────────────────────────
  // POST   /instances?ttl=
  // GET    /instances
  // GET    /instances/{id}
  // DELETE /instances/{id}?cleanup=
  // POST   /instances/{id}/renew?ttl=

  public MitmProxyCreateInstanceResponse createInstance(Integer ttl) throws Exception {
    String path = "instances";
    if (ttl != null) {
      path += "?ttl=" + ttl;
    }
    String response = sendWithRetry("POST", path, null);
    return MapperHelper.toObject(response, MitmProxyCreateInstanceResponse.class);
  }

  public MitmProxyCreateInstanceResponse createInstance() throws Exception {
    return createInstance(null);
  }

  public List<MitmProxyInstanceSummary> listInstances() throws Exception {
    String response = sendWithRetry("GET", "instances", null);
    List<MitmProxyInstanceSummary> list = MapperHelper.toObject(response,
        new TypeReference<List<MitmProxyInstanceSummary>>() {});
    return list != null ? list : Collections.emptyList();
  }

  public MitmProxyInstanceDetail getInstance(String instanceId) throws Exception {
    String response = sendWithRetry("GET", "instances/" + instanceId, null);
    return MapperHelper.toObject(response, MitmProxyInstanceDetail.class);
  }

  public MitmProxyMessageResponse destroyInstance(String instanceId, boolean cleanup) throws Exception {
    String path = "instances/" + instanceId;
    if (cleanup) {
      path += "?cleanup=true";
    }
    String response = sendWithRetry("DELETE", path, null);
    return MapperHelper.toObject(response, MitmProxyMessageResponse.class);
  }

  public MitmProxyMessageResponse destroyInstance(String instanceId) throws Exception {
    return destroyInstance(instanceId, false);
  }

  public MitmProxyRenewResponse renewInstance(String instanceId, Integer ttl) throws Exception {
    String path = "instances/" + instanceId + "/renew";
    if (ttl != null) {
      path += "?ttl=" + ttl;
    }
    String response = sendWithRetry("POST", path, null);
    return MapperHelper.toObject(response, MitmProxyRenewResponse.class);
  }

  public MitmProxyRenewResponse renewInstance(String instanceId) throws Exception {
    return renewInstance(instanceId, null);
  }

  // ── Rules (instance-scoped) ───────────────────────────────────────
  // POST   /instances/{id}/rules
  // GET    /instances/{id}/rules
  // DELETE /instances/{id}/rules/{index}
  // PATCH  /instances/{id}/rules/{index}/toggle

  public MitmProxyMessageResponse createRule(String instanceId, MitmProxyRule rule) throws Exception {
    String body = MapperHelper.toString(rule);
    String response = sendWithRetry("POST", "instances/" + instanceId + "/rules", body);
    return MapperHelper.toObject(response, MitmProxyMessageResponse.class);
  }

  public List<MitmProxyRuleResponse> listRules(String instanceId) throws Exception {
    String response = sendWithRetry("GET", "instances/" + instanceId + "/rules", null);
    List<MitmProxyRuleResponse> rules = MapperHelper.toObject(response,
        new TypeReference<List<MitmProxyRuleResponse>>() {});
    return rules != null ? rules : Collections.emptyList();
  }

  public MitmProxyMessageResponse deleteRule(String instanceId, int ruleIndex) throws Exception {
    String response = sendWithRetry("DELETE",
        "instances/" + instanceId + "/rules/" + ruleIndex, null);
    return MapperHelper.toObject(response, MitmProxyMessageResponse.class);
  }

  public MitmProxyMessageResponse toggleRule(String instanceId, int ruleIndex) throws Exception {
    String response = sendWithRetry("PATCH",
        "instances/" + instanceId + "/rules/" + ruleIndex + "/toggle", null);
    return MapperHelper.toObject(response, MitmProxyMessageResponse.class);
  }

  /**
   * Remove all rules from an instance (deletes from highest index to lowest).
   */
  public void clearAllRules(String instanceId) throws Exception {
    List<MitmProxyRuleResponse> rules = listRules(instanceId);
    for (int i = rules.size() - 1; i >= 0; i--) {
      deleteRule(instanceId, rules.get(i).getIndex());
    }
  }

  // ── Certificate (instance-scoped) ─────────────────────────────────
  // GET /instances/{id}/cert

  public String getCaCertificate(String instanceId) throws Exception {
    return sendWithRetry("GET", "instances/" + instanceId + "/cert", null);
  }

  // ── HTTP transport ────────────────────────────────────────────────

  private String sendWithRetry(String method, String path, String body) throws Exception {
    Exception lastException = null;
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        return send(method, path, body);
      } catch (Exception e) {
        lastException = e;
        if (attempt < MAX_RETRIES - 1) {
          long backoff = RETRY_BASE_MS * (1L << attempt);
          log.debug("MitmProxy API call failed (attempt {}), retrying in {}ms: {}",
              attempt + 1, backoff, e.getMessage());
          Thread.sleep(backoff);
        }
      }
    }
    throw lastException;
  }

  private String send(String method, String path, String body) throws Exception {
    URI uri = URI.create(baseUrl + path);
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(DEFAULT_TIMEOUT)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json");

    switch (method.toUpperCase()) {
      case "GET" -> builder.GET();
      case "DELETE" -> builder.DELETE();
      case "POST" -> builder.POST(body == null
          ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      case "PUT" -> builder.PUT(body == null
          ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      case "PATCH" -> builder.method("PATCH", body == null
          ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
    }

    HttpResponse<String> response = client.send(
        builder.build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    if (response.statusCode() >= 400) {
      throw new RuntimeException(String.format(
          "MitmProxy API %s %s returned %d: %s",
          method, path, response.statusCode(), response.body()));
    }
    return response.body();
  }

  private static String normalizeUrl(String url) {
    if (url == null || url.isEmpty()) {
      return "http://localhost:8090/";
    }
    return url.endsWith("/") ? url : url + "/";
  }
}
