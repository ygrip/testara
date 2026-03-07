package io.github.ygrip.testara.ui.proxy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.browserup.harreader.model.Har;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.ui.model.CreateBlacklistedRequest;
import io.github.ygrip.testara.ui.model.CreateHarRequest;
import io.github.ygrip.testara.ui.model.CreateProxyRequest;
import io.github.ygrip.testara.ui.model.CreateWhitelistedRequest;
import io.github.ygrip.testara.ui.model.StartNewPageRequest;

/**
 * HTTP client for remote BrowserUp proxy utility. Connects to a standalone
 * BrowserUp instance to start/stop proxies and manage HAR.
 */
public class ProxyHelper {
  private static final String PROXY_PATH = "proxy";
  private final String baseUrl;
  private final HttpClient client;

  /**
   * @param client HTTP client (e.g. {@link HttpClient#newHttpClient()})
   * @param host   base URL of the proxy utility (e.g. "http://localhost:8080/")
   */
  public ProxyHelper(HttpClient client, String host) {
    this.client = client;
    this.baseUrl = host == null || host.isEmpty() ? "http://localhost:8080/" : host.endsWith("/") ? host : host + "/";
  }

  public Integer startProxy(CreateProxyRequest request) throws Exception {
    String body = request != null ? MapperHelper.toString(request) : "{}";
    String responseBody = send("POST", PROXY_PATH, body);
    LinkedHashMap<String, Object> result = MapperHelper.toObject(responseBody,
        new TypeReference<LinkedHashMap<String, Object>>() {});
    Object port = result.get("port");
    return port == null ? 0 : Integer.parseInt(String.valueOf(port));
  }

  public void createHar(CreateHarRequest request, Integer port) throws Exception {
    String body = request != null ? MapperHelper.toString(request) : "{}";
    send("PUT", PROXY_PATH + "/" + port + "/har", body);
  }

  public void createNewPage(StartNewPageRequest request, Integer port) throws Exception {
    String body = request != null ? MapperHelper.toString(request) : "{}";
    send("PUT", PROXY_PATH + "/" + port + "/har/pageRef", body);
  }

  public List<Integer> getAvailableProxies() throws Exception {
    String responseBody = send("GET", PROXY_PATH, null);
    LinkedHashMap<String, Object> result = MapperHelper.toObject(responseBody,
        new TypeReference<LinkedHashMap<String, Object>>() {});
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> proxyList = (List<Map<String, Object>>) result.getOrDefault("proxyList", new ArrayList<>());
    return proxyList.stream()
        .map(m -> m.get("port"))
        .filter(p -> p != null)
        .map(p -> p instanceof Number ? ((Number) p).intValue() : Integer.parseInt(String.valueOf(p)))
        .collect(Collectors.toList());
  }

  public List<String> getWhitelistedUrls(Integer port) throws Exception {
    String responseBody = send("GET", PROXY_PATH + "/" + port + "/whitelist", null);
    return MapperHelper.toObject(responseBody, new TypeReference<List<String>>() {});
  }

  public List<String> getBlacklistedUrls(Integer port) throws Exception {
    String responseBody = send("GET", PROXY_PATH + "/" + port + "/blacklist", null);
    return MapperHelper.toObject(responseBody, new TypeReference<List<String>>() {});
  }

  public void clearBlacklistedUrls(Integer port) throws Exception {
    send("DELETE", PROXY_PATH + "/" + port + "/blacklist", null);
  }

  public void clearWhitelistedUrls(Integer port) throws Exception {
    send("DELETE", PROXY_PATH + "/" + port + "/whitelist", null);
  }

  public void setWhitelistedUrl(CreateWhitelistedRequest request, Integer port) throws Exception {
    String body = request != null ? MapperHelper.toString(request) : "{}";
    send("PUT", PROXY_PATH + "/" + port + "/whitelist", body);
  }

  public void setBlacklistedUrl(CreateBlacklistedRequest request, Integer port) throws Exception {
    String body = request != null ? MapperHelper.toString(request) : "{}";
    send("PUT", PROXY_PATH + "/" + port + "/blacklist", body);
  }

  public void overrideHeaders(Map<String, Object> headers, Integer port) throws Exception {
    String body = headers != null ? MapperHelper.toString(headers) : "{}";
    send("POST", PROXY_PATH + "/" + port + "/headers", body);
  }

  public void stopProxy(Integer port) throws Exception {
    send("DELETE", PROXY_PATH + "/" + port, null);
  }

  public Har getHar(Integer port) throws Exception {
    String responseBody = send("GET", PROXY_PATH + "/" + port + "/har", null);
    return MapperHelper.toObject(responseBody, new TypeReference<Har>() {});
  }

  public boolean isStarted(Integer port) throws Exception {
    if (port == null) {
      return false;
    }
    return getAvailableProxies().contains(port);
  }

  private String send(String method, String path, String body) throws Exception {
    URI uri = URI.create(baseUrl + path);
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(uri)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json");

    switch (method.toUpperCase()) {
      case "GET" -> builder.GET();
      case "DELETE" -> builder.DELETE();
      case "POST" -> builder.POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      case "PUT" -> builder.PUT(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      default -> throw new IllegalArgumentException("Unsupported method: " + method);
    }

    HttpRequest request = builder.build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    if (response.statusCode() >= 400) {
      throw new RuntimeException("Proxy utility returned " + response.statusCode() + ": " + response.body());
    }
    return response.body();
  }
}
