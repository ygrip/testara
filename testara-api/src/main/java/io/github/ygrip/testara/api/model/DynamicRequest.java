package io.github.ygrip.testara.api.model;

import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for creating dynamic request configurations per request in load tests.
 * This provides an intuitive, IDE-friendly API for customizing each request.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Simple header customization
 * .forEachRequest(index -> DynamicRequest.create()
 *     .header("X-Request-Id", "req-" + index)
 *     .build())
 *
 * // Full customization
 * .forEachRequest(index -> DynamicRequest.create()
 *     .url("/api/items/" + index)
 *     .method(Method.POST)
 *     .body(Map.of("itemId", index, "name", "Item " + index))
 *     .header("X-Correlation-Id", UUID.randomUUID().toString())
 *     .queryParam("version", "v2")
 *     .pathParam("userId", "user-" + (index % 10))
 *     .tag("batchId", index / 100)  // Custom data passed to result
 *     .build())
 * }</pre>
 *
 * @author yunaz.ramadhan on 1/17/2026
 * @version $Id: $Id
 */
public final class DynamicRequest {

  private final int requestIndex;
  private Method method;
  private String url;
  private Object body;
  private ContentType contentType;
  private final Map<String, Object> headers = new HashMap<>();
  private final Map<String, Object> queryParams = new HashMap<>();
  private final Map<String, Object> pathParams = new HashMap<>();
  private final Map<String, Object> formParams = new HashMap<>();
  private final List<Cookie> cookies = new ArrayList<>();
  private final Map<String, Object> tags = new HashMap<>();

  private DynamicRequest(int requestIndex) {
    this.requestIndex = requestIndex;
  }

  /**
   * Create a new dynamic request builder.
   *
   * @return new DynamicRequest builder
   */
  public static DynamicRequest create() {
    return new DynamicRequest(-1);
  }

  /**
   * Create a new dynamic request builder with request index.
   *
   * @param requestIndex the index of this request in the batch
   * @return new DynamicRequest builder
   */
  public static DynamicRequest forIndex(int requestIndex) {
    return new DynamicRequest(requestIndex);
  }

  // ==================== URL & METHOD ====================

  /**
   * Set the URL for this request (overrides base URL).
   *
   * @param url the URL endpoint
   * @return this builder
   */
  public DynamicRequest url(String url) {
    this.url = url;
    return this;
  }

  /**
   * Set the HTTP method for this request (overrides base method).
   *
   * @param method the HTTP method
   * @return this builder
   */
  public DynamicRequest method(Method method) {
    this.method = method;
    return this;
  }

  /**
   * Set HTTP method to GET.
   *
   * @return this builder
   */
  public DynamicRequest get() {
    return method(Method.GET);
  }

  /**
   * Set HTTP method to POST.
   *
   * @return this builder
   */
  public DynamicRequest post() {
    return method(Method.POST);
  }

  /**
   * Set HTTP method to PUT.
   *
   * @return this builder
   */
  public DynamicRequest put() {
    return method(Method.PUT);
  }

  /**
   * Set HTTP method to DELETE.
   *
   * @return this builder
   */
  public DynamicRequest delete() {
    return method(Method.DELETE);
  }

  /**
   * Set HTTP method to PATCH.
   *
   * @return this builder
   */
  public DynamicRequest patch() {
    return method(Method.PATCH);
  }

  // ==================== BODY ====================

  /**
   * Set the request body.
   *
   * @param body the request body (will be serialized to JSON)
   * @return this builder
   */
  public DynamicRequest body(Object body) {
    this.body = body;
    return this;
  }

  /**
   * Set the request body as a JSON object using key-value pairs.
   * Convenience method for simple JSON bodies.
   *
   * @param key1 first key
   * @param value1 first value
   * @return this builder
   */
  public DynamicRequest bodyJson(String key1, Object value1) {
    this.body = Map.of(key1, value1);
    return this;
  }

  /**
   * Set the request body as a JSON object using key-value pairs.
   *
   * @param key1 first key
   * @param value1 first value
   * @param key2 second key
   * @param value2 second value
   * @return this builder
   */
  public DynamicRequest bodyJson(String key1, Object value1, String key2, Object value2) {
    this.body = Map.of(key1, value1, key2, value2);
    return this;
  }

  /**
   * Set the request body as a JSON object using key-value pairs.
   *
   * @param key1 first key
   * @param value1 first value
   * @param key2 second key
   * @param value2 second value
   * @param key3 third key
   * @param value3 third value
   * @return this builder
   */
  public DynamicRequest bodyJson(String key1, Object value1, String key2, Object value2,
                                  String key3, Object value3) {
    this.body = Map.of(key1, value1, key2, value2, key3, value3);
    return this;
  }

  /**
   * Set the content type.
   *
   * @param contentType the content type
   * @return this builder
   */
  public DynamicRequest contentType(ContentType contentType) {
    this.contentType = contentType;
    return this;
  }

  /**
   * Set content type to JSON.
   *
   * @return this builder
   */
  public DynamicRequest asJson() {
    return contentType(ContentType.JSON);
  }

  /**
   * Set content type to XML.
   *
   * @return this builder
   */
  public DynamicRequest asXml() {
    return contentType(ContentType.XML);
  }

  /**
   * Set content type to form URL encoded.
   *
   * @return this builder
   */
  public DynamicRequest asForm() {
    return contentType(ContentType.URLENC);
  }

  // ==================== HEADERS ====================

  /**
   * Add a header to this request.
   *
   * @param name header name
   * @param value header value
   * @return this builder
   */
  public DynamicRequest header(String name, Object value) {
    this.headers.put(name, value);
    return this;
  }

  /**
   * Add multiple headers to this request.
   *
   * @param headers map of header names to values
   * @return this builder
   */
  public DynamicRequest headers(Map<String, Object> headers) {
    this.headers.putAll(headers);
    return this;
  }

  /**
   * Add Authorization header with Bearer token.
   *
   * @param token the bearer token
   * @return this builder
   */
  public DynamicRequest bearerToken(String token) {
    return header("Authorization", "Bearer " + token);
  }

  /**
   * Add Authorization header with Basic auth.
   *
   * @param credentials base64 encoded credentials
   * @return this builder
   */
  public DynamicRequest basicAuth(String credentials) {
    return header("Authorization", "Basic " + credentials);
  }

  /**
   * Add X-Request-ID header.
   *
   * @param requestId the request ID
   * @return this builder
   */
  public DynamicRequest requestId(String requestId) {
    return header("X-Request-ID", requestId);
  }

  /**
   * Add X-Correlation-ID header.
   *
   * @param correlationId the correlation ID
   * @return this builder
   */
  public DynamicRequest correlationId(String correlationId) {
    return header("X-Correlation-ID", correlationId);
  }

  // ==================== QUERY PARAMS ====================

  /**
   * Add a query parameter to this request.
   *
   * @param name parameter name
   * @param value parameter value
   * @return this builder
   */
  public DynamicRequest queryParam(String name, Object value) {
    this.queryParams.put(name, value);
    return this;
  }

  /**
   * Add multiple query parameters to this request.
   *
   * @param params map of parameter names to values
   * @return this builder
   */
  public DynamicRequest queryParams(Map<String, Object> params) {
    this.queryParams.putAll(params);
    return this;
  }

  /**
   * Add pagination query parameters.
   *
   * @param page page number
   * @param size page size
   * @return this builder
   */
  public DynamicRequest paginate(int page, int size) {
    return queryParam("page", page).queryParam("size", size);
  }

  /**
   * Add limit and offset query parameters.
   *
   * @param limit result limit
   * @param offset result offset
   * @return this builder
   */
  public DynamicRequest limitOffset(int limit, int offset) {
    return queryParam("limit", limit).queryParam("offset", offset);
  }

  // ==================== PATH PARAMS ====================

  /**
   * Add a path parameter to this request.
   * Path parameters replace {name} placeholders in the URL.
   *
   * @param name parameter name
   * @param value parameter value
   * @return this builder
   */
  public DynamicRequest pathParam(String name, Object value) {
    this.pathParams.put(name, value);
    return this;
  }

  /**
   * Add multiple path parameters to this request.
   *
   * @param params map of parameter names to values
   * @return this builder
   */
  public DynamicRequest pathParams(Map<String, Object> params) {
    this.pathParams.putAll(params);
    return this;
  }

  // ==================== FORM PARAMS ====================

  /**
   * Add a form parameter to this request.
   *
   * @param name parameter name
   * @param value parameter value
   * @return this builder
   */
  public DynamicRequest formParam(String name, Object value) {
    this.formParams.put(name, value);
    return this;
  }

  /**
   * Add multiple form parameters to this request.
   *
   * @param params map of parameter names to values
   * @return this builder
   */
  public DynamicRequest formParams(Map<String, Object> params) {
    this.formParams.putAll(params);
    return this;
  }

  // ==================== COOKIES ====================

  /**
   * Add a cookie to this request.
   *
   * @param cookie the cookie
   * @return this builder
   */
  public DynamicRequest cookie(Cookie cookie) {
    this.cookies.add(cookie);
    return this;
  }

  /**
   * Add a simple cookie to this request.
   *
   * @param name cookie name
   * @param value cookie value
   * @return this builder
   */
  public DynamicRequest cookie(String name, String value) {
    this.cookies.add(new Cookie.Builder(name, value).build());
    return this;
  }

  // ==================== TAGS (Custom Data) ====================

  /**
   * Add a custom tag to this request.
   * Tags are passed through to the result for identification/grouping.
   *
   * @param name tag name
   * @param value tag value
   * @return this builder
   */
  public DynamicRequest tag(String name, Object value) {
    this.tags.put(name, value);
    return this;
  }

  /**
   * Add multiple tags to this request.
   *
   * @param tags map of tag names to values
   * @return this builder
   */
  public DynamicRequest tags(Map<String, Object> tags) {
    this.tags.putAll(tags);
    return this;
  }

  // ==================== BUILD ====================

  /**
   * Build the RequestContext from this builder.
   *
   * @return the built RequestContext
   */
  public RequestContext build() {
    return RequestContext.builder()
        .requestIndex(requestIndex)
        .method(method)
        .url(url)
        .body(body)
        .contentType(contentType)
        .headers(headers.isEmpty() ? null : new HashMap<>(headers))
        .queryParams(queryParams.isEmpty() ? null : new HashMap<>(queryParams))
        .pathParams(pathParams.isEmpty() ? null : new HashMap<>(pathParams))
        .formParams(formParams.isEmpty() ? null : new HashMap<>(formParams))
        .cookies(cookies.isEmpty() ? null : new ArrayList<>(cookies))
        .customData(tags.isEmpty() ? null : new HashMap<>(tags))
        .build();
  }

  /**
   * Get the request index.
   *
   * @return request index
   */
  public int getRequestIndex() {
    return requestIndex;
  }
}
