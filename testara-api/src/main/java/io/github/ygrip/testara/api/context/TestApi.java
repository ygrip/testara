package io.github.ygrip.testara.api.context;

import io.github.ygrip.testara.api.ConcurrentRequestBuilder;
import io.github.ygrip.testara.api.ConcurrentRequestBuilderImpl;
import io.github.ygrip.testara.api.RequestBuilder;
import io.github.ygrip.testara.api.data.ApiResponseData;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;

/**
 * Static entry point for API testing utilities.
 * Provides access to REST request builders and response data.
 */
public final class TestApi {

  private TestApi() {
    // Utility class
  }

  /**
   * Get the REST request builder for making API calls.
   *
   * @return RequestBuilder instance
   */
  public static RequestBuilder rest() {
    return TestFramework.context().get(RequestBuilder.class);
  }

  /**
   * Get the REST request builder configured for a specific service.
   *
   * @param serviceName the service configuration name
   * @return RequestBuilder instance configured for the service
   * @throws Exception if service configuration fails
   */
  public static RequestBuilder rest(String serviceName) throws Exception {
    return rest().setService(serviceName);
  }

  /**
   * Get the latest API response data.
   *
   * @return ApiResponseData containing the latest response
   */
  public static ApiResponseData response() {
    return TestFramework.context().get(DataHolder.class).getResponse(ApiResponseData.class);
  }

  /**
   * Get the concurrent request builder for load testing.
   * This builder allows sending multiple requests simultaneously.
   *
   * <p>Example usage:
   * <pre>{@code
   * LoadTestSummary summary = TestApi.loadTest()
   *     .setService("my-api")
   *     .withMethod(Method.GET)
   *     .withUrl("/api/endpoint")
   *     .withConcurrency(10)
   *     .withTotalRequests(100)
   *     .executeAndSummarize();
   * }</pre>
   *
   * @return ConcurrentRequestBuilder instance
   */
  public static ConcurrentRequestBuilder loadTest() {
    return TestFramework.context().get(ConcurrentRequestBuilderImpl.class);
  }

  /**
   * Get the concurrent request builder configured for a specific service.
   *
   * <p>Example usage:
   * <pre>{@code
   * LoadTestSummary summary = TestApi.loadTest("my-api")
   *     .withMethod(Method.POST)
   *     .withUrl("/api/create")
   *     .withDynamicBody(index -> Map.of("id", "item-" + index))
   *     .withConcurrency(20)
   *     .withTotalRequests(500)
   *     .withLightweightMode(true)  // Memory efficient
   *     .executeAndSummarize();
   * }</pre>
   *
   * @param serviceName the service configuration name
   * @return ConcurrentRequestBuilder instance configured for the service
   */
  public static ConcurrentRequestBuilder loadTest(String serviceName) {
    return loadTest().setService(serviceName);
  }

  /**
   * Get the concurrent request builder in lightweight mode for memory-efficient load testing.
   * In lightweight mode, response bodies and exceptions are not stored.
   *
   * <p>Example usage:
   * <pre>{@code
   * LoadTestSummary summary = TestApi.loadTestLightweight()
   *     .withMethod(Method.GET)
   *     .withUrl("http://api.example.com/health")
   *     .withConcurrency(50)
   *     .withTotalRequests(10000)
   *     .executeAndSummarize();
   * }</pre>
   *
   * @return ConcurrentRequestBuilder instance in lightweight mode
   */
  public static ConcurrentRequestBuilder loadTestLightweight() {
    return loadTest().withLightweightMode(true);
  }

  /**
   * Get the concurrent request builder configured for a specific service in lightweight mode.
   *
   * @param serviceName the service configuration name
   * @return ConcurrentRequestBuilder instance in lightweight mode
   */
  public static ConcurrentRequestBuilder loadTestLightweight(String serviceName) {
    return loadTest(serviceName).withLightweightMode(true);
  }
}
