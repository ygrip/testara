package io.github.ygrip.testara.api.model;

import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Method;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object for dynamically configuring each request in a concurrent load test.
 * Allows complete customization of individual requests.
 *
 * @author yunaz.ramadhan on 1/17/2026
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestContext {

  /**
   * The request index (0-based)
   */
  private int requestIndex;

  /**
   * HTTP method override for this specific request
   */
  private Method method;

  /**
   * URL override for this specific request
   */
  private String url;

  /**
   * Request body for this specific request
   */
  private Object body;

  /**
   * Content type override for this specific request
   */
  private ContentType contentType;

  /**
   * Additional headers for this request (merged with base headers)
   */
  @Builder.Default
  private Map<String, Object> headers = new HashMap<>();

  /**
   * Additional query parameters for this request (merged with base params)
   */
  @Builder.Default
  private Map<String, Object> queryParams = new HashMap<>();

  /**
   * Path parameters for this request (merged with base params)
   */
  @Builder.Default
  private Map<String, Object> pathParams = new HashMap<>();

  /**
   * Form parameters for this request (merged with base params)
   */
  @Builder.Default
  private Map<String, Object> formParams = new HashMap<>();

  /**
   * Cookies for this request
   */
  private List<Cookie> cookies;

  /**
   * Custom data that can be passed through to result processing
   */
  @Builder.Default
  private Map<String, Object> customData = new HashMap<>();

  /**
   * Create a simple context with just body variation
   *
   * @param requestIndex the request index
   * @param body the request body
   * @return new RequestContext
   */
  public static RequestContext withBody(int requestIndex, Object body) {
    return RequestContext.builder()
        .requestIndex(requestIndex)
        .body(body)
        .build();
  }

  /**
   * Create a context with URL and body variation
   *
   * @param requestIndex the request index
   * @param url the URL for this request
   * @param body the request body
   * @return new RequestContext
   */
  public static RequestContext withUrlAndBody(int requestIndex, String url, Object body) {
    return RequestContext.builder()
        .requestIndex(requestIndex)
        .url(url)
        .body(body)
        .build();
  }

  /**
   * Create a context with query parameter variation
   *
   * @param requestIndex the request index
   * @param queryParams the query parameters
   * @return new RequestContext
   */
  public static RequestContext withQueryParams(int requestIndex, Map<String, Object> queryParams) {
    return RequestContext.builder()
        .requestIndex(requestIndex)
        .queryParams(queryParams)
        .build();
  }

  /**
   * Add a header to this context
   *
   * @param key header key
   * @param value header value
   * @return this context for chaining
   */
  public RequestContext addHeader(String key, Object value) {
    if (this.headers == null) {
      this.headers = new HashMap<>();
    }
    this.headers.put(key, value);
    return this;
  }

  /**
   * Add a query parameter to this context
   *
   * @param key parameter key
   * @param value parameter value
   * @return this context for chaining
   */
  public RequestContext addQueryParam(String key, Object value) {
    if (this.queryParams == null) {
      this.queryParams = new HashMap<>();
    }
    this.queryParams.put(key, value);
    return this;
  }

  /**
   * Add a path parameter to this context
   *
   * @param key parameter key
   * @param value parameter value
   * @return this context for chaining
   */
  public RequestContext addPathParam(String key, Object value) {
    if (this.pathParams == null) {
      this.pathParams = new HashMap<>();
    }
    this.pathParams.put(key, value);
    return this;
  }

  /**
   * Add custom data to pass through to result
   *
   * @param key data key
   * @param value data value
   * @return this context for chaining
   */
  public RequestContext addCustomData(String key, Object value) {
    if (this.customData == null) {
      this.customData = new HashMap<>();
    }
    this.customData.put(key, value);
    return this;
  }
}
