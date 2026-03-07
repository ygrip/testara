package io.github.ygrip.testara.api;

import io.github.ygrip.testara.api.logging.LoadTestFileLogger;
import io.github.ygrip.testara.api.model.ConcurrentRequestResult;
import io.github.ygrip.testara.api.model.CreateRequestSpecification;
import io.github.ygrip.testara.api.model.DynamicRequest;
import io.github.ygrip.testara.api.model.LoadTestSummary;
import io.github.ygrip.testara.api.model.RequestContext;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder interface for concurrent/load test request execution.
 * Supports sending multiple requests simultaneously for performance testing.
 *
 * @author yunaz.ramadhan on 1/17/2026
 * @version $Id: $Id
 */
public interface ConcurrentRequestBuilder {

  /**
   * Set the service name for the requests
   *
   * @param serviceName the service configuration name
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder setService(String serviceName);

  /**
   * Set the number of concurrent requests to execute
   *
   * @param count number of concurrent requests
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withConcurrency(int count);

  /**
   * Set the total number of requests to execute
   * The requests will be distributed across the available threads
   *
   * @param totalRequests total number of requests
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withTotalRequests(int totalRequests);

  /**
   * Set ramp-up duration - time to gradually start all threads
   *
   * @param duration ramp-up duration
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withRampUp(Duration duration);

  /**
   * Set the HTTP method for the requests
   *
   * @param method HTTP method
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withMethod(Method method);

  /**
   * Set the HTTP method for the requests
   *
   * @param method HTTP method as string
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withMethod(String method);

  /**
   * Set the URL endpoint for the requests
   *
   * @param url target URL
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withUrl(String url);

  /**
   * Set the request body
   *
   * @param body request body object
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withBody(Object body);

  /**
   * Set the content type for the requests
   *
   * @param contentType content type
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withContentType(ContentType contentType);

  /**
   * Add headers to the requests
   *
   * @param headers map of header key-value pairs
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withHeaders(Map<String, Object> headers);

  /**
   * Add a single header to the requests
   *
   * @param key header key
   * @param value header value
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withHeader(String key, String value);

  /**
   * Add query parameters to the requests
   *
   * @param params map of query parameter key-value pairs
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withQueryParams(Map<String, Object> params);

  /**
   * Add a single query parameter to the requests
   *
   * @param key parameter key
   * @param value parameter value
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withQueryParam(String key, Object value);

  /**
   * Add path parameters to the requests
   *
   * @param params map of path parameter key-value pairs
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withPathParams(Map<String, Object> params);

  /**
   * Add a single path parameter to the requests
   *
   * @param key parameter key
   * @param value parameter value
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withPathParam(String key, Object value);

  /**
   * Add form parameters to the requests
   *
   * @param params map of form parameter key-value pairs
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withFormParams(Map<String, Object> params);

  /**
   * Add cookies to the requests
   *
   * @param cookies list of cookies
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withCookies(List<Cookie> cookies);

  /**
   * Set a request specification template
   *
   * @param specification the request specification template
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder fromSpecification(CreateRequestSpecification specification);

  /**
   * Load request specification from file path
   *
   * @param specificationPath path to the specification file
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder fromSpecificationPath(String specificationPath);

  /**
   * Configure each request dynamically using a fluent builder.
   * This is the recommended way to customize requests per iteration.
   *
   * <p>Example usage:
   * <pre>{@code
   * // Add unique header per request
   * .forEachRequest(index -> DynamicRequest.create()
   *     .header("X-Request-Id", "req-" + index)
   *     .build())
   *
   * // Different body per request
   * .forEachRequest(index -> DynamicRequest.create()
   *     .bodyJson("itemId", index, "name", "Item " + index)
   *     .build())
   *
   * // Full customization
   * .forEachRequest(index -> DynamicRequest.create()
   *     .url("/api/users/" + (index % 100))
   *     .header("X-Correlation-Id", UUID.randomUUID().toString())
   *     .queryParam("include", "details")
   *     .tag("batch", index / 100)
   *     .build())
   * }</pre>
   *
   * @param requestBuilder function that receives request index and returns DynamicRequest
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder forEachRequest(Function<Integer, DynamicRequest> requestBuilder);

  /**
   * Set a customizer function that can modify each request before execution.
   * Consider using {@link #forEachRequest(Function)} for a more fluent API.
   *
   * @param customizer function that receives request index and returns modifications
   * @return this builder for fluent chaining
   * @deprecated Use {@link #forEachRequest(Function)} instead for a more fluent API
   */
  @Deprecated
  ConcurrentRequestBuilder withRequestCustomizer(Function<Integer, Map<String, Object>> customizer);

  /**
   * Set a dynamic body supplier that generates a new body for each request.
   * Consider using {@link #forEachRequest(Function)} for a more fluent API.
   *
   * @param bodySupplier function that receives request index and returns the body
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withDynamicBody(Function<Integer, Object> bodySupplier);

  /**
   * Set a dynamic URL supplier that generates a URL for each request.
   * Consider using {@link #forEachRequest(Function)} for a more fluent API.
   *
   * @param urlSupplier function that receives request index and returns the URL
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withDynamicUrl(Function<Integer, String> urlSupplier);

  /**
   * Set a full request context supplier for complete control over each request.
   * Consider using {@link #forEachRequest(Function)} for a more fluent API.
   *
   * @param contextSupplier function that receives request index and returns RequestContext
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withDynamicContext(Function<Integer, RequestContext> contextSupplier);

  /**
   * Enable lightweight mode for memory efficiency.
   * In lightweight mode:
   * - Response bodies are not stored (reduces memory)
   * - Response objects are not retained
   * - Only essential metrics are collected
   *
   * @param lightweight true to enable lightweight mode
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withLightweightMode(boolean lightweight);

  /**
   * Set maximum response body size to store (in bytes).
   * Larger responses will be truncated. Set to 0 to not store response bodies.
   *
   * @param maxBytes maximum bytes to store per response (0 = don't store)
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withMaxResponseBodySize(int maxBytes);

  /**
   * Set a callback to be invoked after each request completes
   *
   * @param callback consumer that receives each result
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder onEachResult(Consumer<ConcurrentRequestResult> callback);

  /**
   * Set timeout for each individual request
   *
   * @param timeout request timeout duration
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withTimeout(Duration timeout);

  /**
   * Enable or disable following redirects
   *
   * @param follow whether to follow redirects
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder followRedirects(boolean follow);

  /**
   * Execute the concurrent requests and return individual results
   *
   * @return list of individual request results
   * @throws Exception if execution fails
   */
  List<ConcurrentRequestResult> execute() throws Exception;

  /**
   * Execute the concurrent requests and return aggregated summary
   *
   * @return load test summary with statistics
   * @throws Exception if execution fails
   */
  LoadTestSummary executeAndSummarize() throws Exception;

  /**
   * Execute requests with a steady rate (requests per second)
   *
   * @param requestsPerSecond target rate
   * @param duration total duration to maintain the rate
   * @return load test summary
   * @throws Exception if execution fails
   */
  LoadTestSummary executeWithRate(double requestsPerSecond, Duration duration) throws Exception;

  /**
   * Reset the builder to its initial state
   *
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder reset();

  /**
   * Get the underlying request specification for advanced customization
   *
   * @return the request specification
   * @throws Exception if specification is not initialized
   */
  RequestSpecification getSpecification() throws Exception;

  // ==================== LOGGING CONFIGURATION ====================

  /**
   * Enable file logging for requests and responses.
   * Logs are written asynchronously to target/load-test-logs/{testName}_{timestamp}/
   *
   * @param testName descriptive name for the test (used in log folder name)
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withFileLogging(String testName);

  /**
   * Enable file logging with custom output directory.
   *
   * @param testName descriptive name for the test
   * @param outputDir custom output directory path
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withFileLogging(String testName, Path outputDir);

  /**
   * Set a custom file logger instance.
   * Useful for sharing a logger across multiple test runs.
   *
   * @param logger the file logger instance
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withFileLogger(LoadTestFileLogger logger);

  /**
   * Enable console logging for request/response debugging.
   * Warning: This can generate a lot of output for high-volume tests.
   *
   * @param enable true to enable console logging
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withConsoleLogging(boolean enable);

  /**
   * Enable logging of request details (method, URL, headers, body).
   * Only logged if file logging or console logging is enabled.
   *
   * @param logRequests true to log request details
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder logRequests(boolean logRequests);

  /**
   * Enable logging of response details (status, headers, body).
   * Only logged if file logging or console logging is enabled.
   *
   * @param logResponses true to log response details
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder logResponses(boolean logResponses);

  /**
   * Set maximum body size to log (in characters).
   * Bodies larger than this will be truncated.
   *
   * @param maxSize maximum characters to log (default: 10000)
   * @return this builder for fluent chaining
   */
  ConcurrentRequestBuilder withLogBodyMaxSize(int maxSize);

  /**
   * Get the file logger instance (if enabled).
   *
   * @return the file logger, or null if not enabled
   */
  LoadTestFileLogger getFileLogger();
}
