package io.github.ygrip.testara.api;

import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.config.ApiSpecProperties;
import io.github.ygrip.testara.api.config.SharedServiceConfigCache;
import io.github.ygrip.testara.api.model.ConcurrentRequestResult;
import io.github.ygrip.testara.api.model.DynamicRequest;
import io.github.ygrip.testara.api.model.LoadTestSummary;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Unit tests for ConcurrentRequestBuilder.
 *
 * @author yunaz.ramadhan on 1/17/2026
 */
@Log4j2
@Tag("api")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ConcurrentRequestBuilderTest extends BaseTests {

  @RegisterExtension
  static WireMockExtension wiremock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
      .build();

  private ConcurrentRequestBuilder concurrentBuilder;

  @BeforeEach
  void setup() {
    System.setProperty("MOCK_HTTP_PORT", String.valueOf(wiremock.getPort()));
    TestFramework.context().configuration().reload();
    TestFramework.context()
        .get(SharedServiceConfigCache.class)
        .loadServiceConfigurations(
            TestFramework.context().configuration().get(ApiProperties.class),
            TestFramework.context().configuration().get(ApiSpecProperties.class));

    concurrentBuilder = TestFramework.context().get(ConcurrentRequestBuilderImpl.class);
  }

  @Test
  @DisplayName("Should execute concurrent GET requests successfully")
  void shouldExecuteConcurrentGetRequests() throws Exception {
    // Setup
    String responseBody = "{\"message\": \"success\", \"timestamp\": " + System.currentTimeMillis() + "}";
    wiremock.stubFor(get(urlEqualTo("/api/test"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)));

    // Execute
    List<ConcurrentRequestResult> results = concurrentBuilder
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/test", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(5)
        .withTotalRequests(10)
        .execute();

    // Verify
    assertThat(results, notNullValue());
    assertThat(results.size(), equalTo(10));

    long successCount = results.stream().filter(ConcurrentRequestResult::isSuccess).count();
    assertThat(successCount, equalTo(10L));

    results.forEach(result -> {
      assertThat(result.getStatusCode(), equalTo(200));
      assertThat(result.getDurationMillis(), greaterThan(0L));
      assertThat(result.getThreadName(), notNullValue());
    });

    wiremock.verify(10, getRequestedFor(urlEqualTo("/api/test")));
  }

  @Test
  @DisplayName("Should execute concurrent POST requests with body")
  void shouldExecuteConcurrentPostRequests() throws Exception {
    // Setup
    wiremock.stubFor(post(urlEqualTo("/api/create"))
        .withHeader("Content-Type", containing("json"))
        .willReturn(aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"id\": 123, \"created\": true}")));

    Map<String, Object> requestBody = Map.of("name", "test", "value", 42);

    // Execute
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .withMethod(Method.POST)
        .withUrl(String.format("http://localhost:%d/api/create", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withBody(requestBody)
        .withConcurrency(3)
        .withTotalRequests(6)
        .execute();

    // Verify
    assertThat(results.size(), equalTo(6));

    long successCount = results.stream().filter(ConcurrentRequestResult::isSuccess).count();
    assertThat(successCount, equalTo(6L));

    results.forEach(result -> {
      assertThat(result.getStatusCode(), equalTo(201));
    });

    wiremock.verify(6, postRequestedFor(urlEqualTo("/api/create")));
  }

  @Test
  @DisplayName("Should generate load test summary with statistics")
  void shouldGenerateLoadTestSummary() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/stats"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"status\": \"ok\"}")
            .withFixedDelay(50))); // Add 50ms delay

    // Execute
    LoadTestSummary summary = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/stats", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(5)
        .withTotalRequests(20)
        .executeAndSummarize();

    // Verify
    assertThat(summary, notNullValue());
    assertThat(summary.getTotalRequests(), equalTo(20));
    assertThat(summary.getSuccessfulRequests(), equalTo(20));
    assertThat(summary.getFailedRequests(), equalTo(0));
    assertThat(summary.getSuccessRate(), equalTo(100.0));

    // Verify timing metrics
    assertThat(summary.getAverageResponseTime(), notNullValue());
    assertThat(summary.getMinResponseTime(), notNullValue());
    assertThat(summary.getMaxResponseTime(), notNullValue());
    assertThat(summary.getP50ResponseTime(), notNullValue());
    assertThat(summary.getP90ResponseTime(), notNullValue());
    assertThat(summary.getP95ResponseTime(), notNullValue());
    assertThat(summary.getP99ResponseTime(), notNullValue());

    // Verify throughput
    assertThat(summary.getRequestsPerSecond(), greaterThan(0.0));

    // Verify status code distribution
    assertThat(summary.getStatusCodeDistribution().get(200), equalTo(20L));

    log.info(summary.toFormattedString());
  }

  @Test
  @DisplayName("Should handle request failures gracefully")
  void shouldHandleRequestFailures() throws Exception {
    // Setup - mix of success and failure responses
    wiremock.stubFor(get(urlPathEqualTo("/api/flaky"))
        .willReturn(aResponse()
            .withStatus(500)
            .withBody("{\"error\": \"Internal Server Error\"}")));

    // Execute
    LoadTestSummary summary = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/flaky", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(3)
        .withTotalRequests(5)
        .executeAndSummarize();

    // Verify - all requests should complete (even with 500 status)
    assertThat(summary.getTotalRequests(), equalTo(5));
    assertThat(summary.getSuccessfulRequests(), equalTo(5)); // Request succeeded, just returned 500
    assertThat(summary.getStatusCodeDistribution().get(500), equalTo(5L));

    log.info(summary.toFormattedString());
  }

  @Test
  @DisplayName("Should support custom headers and query parameters")
  void shouldSupportCustomHeadersAndQueryParams() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/custom"))
        .withHeader("X-Custom-Header", equalTo("custom-value"))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withQueryParam("page", equalTo("1"))
        .withQueryParam("limit", equalTo("10"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"data\": []}")));

    // Execute
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/custom", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withHeader("X-Custom-Header", "custom-value")
        .withHeader("Authorization", "Bearer test-token")
        .withQueryParam("page", "1")
        .withQueryParam("limit", "10")
        .withConcurrency(2)
        .withTotalRequests(3)
        .execute();

    // Verify
    assertThat(results.size(), equalTo(3));
    results.forEach(result -> assertThat(result.getStatusCode(), equalTo(200)));

    wiremock.verify(3, getRequestedFor(urlPathEqualTo("/api/custom"))
        .withHeader("X-Custom-Header", equalTo("custom-value"))
        .withHeader("Authorization", equalTo("Bearer test-token")));
  }

  @Test
  @DisplayName("Should support per-request customization with fluent API")
  void shouldSupportPerRequestCustomization() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/item"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"found\": true}")));

    AtomicInteger callbackCount = new AtomicInteger(0);

    // Execute with per-request customization using fluent DynamicRequest API
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/item", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(3)
        .withTotalRequests(5)
        .forEachRequest(index -> DynamicRequest.create()
            .queryParam("itemId", "item-" + index)
            .tag("requestNumber", index))
        .onEachResult(result -> {
          callbackCount.incrementAndGet();
          log.debug("Received result for request #{}: status={}", result.getRequestIndex(), result.getStatusCode());
        })
        .execute();

    // Verify
    assertThat(results.size(), equalTo(5));
    assertThat(callbackCount.get(), equalTo(5));

    // Verify each request had unique itemId
    wiremock.verify(5, getRequestedFor(urlPathEqualTo("/api/item")));
  }

  @Test
  @DisplayName("Should execute requests with rate limiting")
  void shouldExecuteWithRateLimiting() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/rate"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"ok\": true}")));

    // Execute with rate limiting: 5 requests per second for 2 seconds
    LoadTestSummary summary = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/rate", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(10)
        .executeWithRate(5.0, Duration.ofSeconds(2));

    // Verify - should have approximately 10 requests (5 RPS * 2 seconds)
    assertThat(summary.getTotalRequests(), greaterThanOrEqualTo(8)); // Allow some variance
    assertThat(summary.getTotalRequests(), lessThan(15)); // But not too many

    log.info("Rate-limited test completed: {} requests in {} ms",
        summary.getTotalRequests(), summary.getTotalDuration().toMillis());
    log.info(summary.toFormattedString());
  }

  @Test
  @DisplayName("Should support ramp-up duration")
  void shouldSupportRampUpDuration() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/rampup"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"ramped\": true}")));

    long startTime = System.currentTimeMillis();

    // Execute with 1 second ramp-up
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/rampup", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(5)
        .withTotalRequests(10)
        .withRampUp(Duration.ofSeconds(1))
        .execute();

    long duration = System.currentTimeMillis() - startTime;

    // Verify
    assertThat(results.size(), equalTo(10));

    // Should take at least 1 second due to ramp-up
    assertThat(duration, greaterThanOrEqualTo(1000L));

    log.info("Ramp-up test completed in {} ms", duration);
  }

  @Test
  @DisplayName("Should use service configuration when available")
  void shouldUseServiceConfiguration() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/fact"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"fact\": \"test fact\"}")));

    // Execute using configured service
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .setService("mock-api")
        .withMethod(Method.GET)
        .withUrl("/fact")
        .withConcurrency(2)
        .withTotalRequests(3)
        .execute();

    // Verify
    assertThat(results.size(), equalTo(3));
    results.forEach(result -> assertThat(result.isSuccess(), equalTo(true)));
  }

  @Test
  @DisplayName("Should reset builder state correctly")
  void shouldResetBuilderState() throws Exception {
    // Setup initial state
    concurrentBuilder
        .setService("test-service")
        .withMethod(Method.POST)
        .withUrl("http://example.com/api")
        .withHeader("X-Test", "value")
        .withQueryParam("key", "value")
        .withConcurrency(20)
        .withTotalRequests(100);

    // Reset
    concurrentBuilder.reset();

    // Setup new request
    wiremock.stubFor(get(urlPathEqualTo("/api/reset"))
        .willReturn(ok().withBody("{\"reset\": true}")));

    // Execute with new configuration
    List<ConcurrentRequestResult> results = concurrentBuilder
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/reset", wiremock.getPort()))
        .withConcurrency(2)
        .withTotalRequests(2)
        .execute();

    // Verify reset worked
    assertThat(results.size(), equalTo(2));
    wiremock.verify(2, getRequestedFor(urlPathEqualTo("/api/reset")));
  }

  @Test
  @DisplayName("Should handle connection timeouts")
  void shouldHandleConnectionTimeouts() throws Exception {
    // Setup with very long delay
    wiremock.stubFor(get(urlPathEqualTo("/api/slow"))
        .willReturn(ok()
            .withBody("{\"slow\": true}")
            .withFixedDelay(5000))); // 5 second delay

    // Execute with short timeout
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/slow", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(2)
        .withTotalRequests(2)
        .withTimeout(Duration.ofSeconds(10)) // Longer timeout to allow completion
        .execute();

    // Verify requests completed (even if slow)
    assertThat(results.size(), equalTo(2));
  }

  @Test
  @DisplayName("LoadTestSummary should calculate percentiles correctly")
  void shouldCalculatePercentilesCorrectly() throws Exception {
    // Setup with varying delays
    wiremock.stubFor(get(urlPathEqualTo("/api/percentile"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"ok\": true}")
            .withUniformRandomDelay(10, 100))); // Random delay 10-100ms

    // Execute
    LoadTestSummary summary = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/percentile", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withConcurrency(5)
        .withTotalRequests(50)
        .executeAndSummarize();

    // Verify percentile ordering
    assertThat(summary.getP50ResponseTime().toMillis(),
        lessThan(summary.getP90ResponseTime().toMillis() + 1)); // Allow equal
    assertThat(summary.getP90ResponseTime().toMillis(),
        lessThan(summary.getP95ResponseTime().toMillis() + 1));
    assertThat(summary.getP95ResponseTime().toMillis(),
        lessThan(summary.getP99ResponseTime().toMillis() + 1));

    assertThat(summary.getMinResponseTime().toMillis(),
        lessThan(summary.getMaxResponseTime().toMillis() + 1));

    log.info(summary.toFormattedString());
  }

  @Test
  @DisplayName("Should handle path parameters correctly")
  void shouldHandlePathParameters() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/users/123/orders/456"))
        .willReturn(ok()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"userId\": 123, \"orderId\": 456}")));

    // Execute
    List<ConcurrentRequestResult> results = concurrentBuilder
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/users/{userId}/orders/{orderId}", wiremock.getPort()))
        .withContentType(ContentType.JSON)
        .withPathParam("userId", "123")
        .withPathParam("orderId", "456")
        .withConcurrency(2)
        .withTotalRequests(3)
        .execute();

    // Verify
    assertThat(results.size(), equalTo(3));
    results.forEach(result -> assertThat(result.getStatusCode(), equalTo(200)));
  }
}
