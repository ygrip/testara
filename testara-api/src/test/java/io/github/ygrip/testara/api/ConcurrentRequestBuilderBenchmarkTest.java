package io.github.ygrip.testara.api;

import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.config.ApiSpecProperties;
import io.github.ygrip.testara.api.config.SharedServiceConfigCache;
import io.github.ygrip.testara.api.context.TestApi;
import io.github.ygrip.testara.api.model.ConcurrentRequestResult;
import io.github.ygrip.testara.api.model.DynamicRequest;
import io.github.ygrip.testara.api.model.LoadTestSummary;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.http.Method;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Benchmark tests for ConcurrentRequestBuilder.
 * Tests performance, throughput, memory efficiency, and dynamic value handling.
 *
 * @author yunaz.ramadhan on 1/17/2026
 */
@Log4j2
@Tag("benchmark")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ConcurrentRequestBuilderBenchmarkTest extends BaseTests {

  @RegisterExtension
  static WireMockExtension wiremock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
      .build();

  @BeforeEach
  void setup() {
    System.setProperty("MOCK_HTTP_PORT", String.valueOf(wiremock.getPort()));
    TestFramework.context().configuration().reload();
    TestFramework.context()
        .get(SharedServiceConfigCache.class)
        .loadServiceConfigurations(
            TestFramework.context().configuration().get(ApiProperties.class),
            TestFramework.context().configuration().get(ApiSpecProperties.class));
  }

  // ==================== DYNAMIC VALUE TESTS ====================

  @Test
  @DisplayName("Should handle dynamic body per request")
  void shouldHandleDynamicBodyPerRequest() throws Exception {
    // Setup
    wiremock.stubFor(post(urlPathEqualTo("/api/dynamic"))
        .willReturn(aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"created\": true}")));

    // Execute with dynamic body using fluent API
    List<ConcurrentRequestResult> results = TestApi.loadTest()
        .reset()
        .withMethod(Method.POST)
        .withUrl(String.format("http://localhost:%d/api/dynamic", wiremock.getPort()))
        .forEachRequest(index -> DynamicRequest.create()
            .bodyJson("requestId", UUID.randomUUID().toString(),
                "index", index,
                "timestamp", System.currentTimeMillis()))
        .withConcurrency(5)
        .withTotalRequests(20)
        .execute();

    // Verify all requests succeeded
    assertThat(results.size(), greaterThanOrEqualTo(20));
    long successCount = results.stream().filter(ConcurrentRequestResult::isSuccess).count();
    assertThat(successCount, greaterThanOrEqualTo(20L));

    log.info("Dynamic body test: {} successful requests", successCount);
  }

  @Test
  @DisplayName("Should handle dynamic URL per request")
  void shouldHandleDynamicUrlPerRequest() throws Exception {
    // Setup multiple endpoints
    for (int i = 0; i < 10; i++) {
      wiremock.stubFor(get(urlPathEqualTo("/api/item/" + i))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("{\"itemId\": " + i + "}")));
    }

    // Execute with dynamic URL using fluent API
    List<ConcurrentRequestResult> results = TestApi.loadTest()
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/item/0", wiremock.getPort())) // Base URL for validation
        .forEachRequest(index -> DynamicRequest.create()
            .url(String.format("http://localhost:%d/api/item/%d", wiremock.getPort(), index % 10)))
        .withConcurrency(5)
        .withTotalRequests(30)
        .execute();

    // Verify
    assertThat(results.size(), greaterThanOrEqualTo(30));
    long successCount = results.stream().filter(ConcurrentRequestResult::isSuccess).count();
    assertThat(successCount, greaterThanOrEqualTo(30L));

    log.info("Dynamic URL test: {} successful requests", successCount);
  }

  @Test
  @DisplayName("Should handle full dynamic context per request")
  void shouldHandleFullDynamicContext() throws Exception {
    // Setup
    wiremock.stubFor(post(urlPathEqualTo("/api/context"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"processed\": true}")));

    // Execute with full dynamic context using fluent API
    List<ConcurrentRequestResult> results = TestApi.loadTest()
        .reset()
        .withMethod(Method.POST)
        .withUrl(String.format("http://localhost:%d/api/context", wiremock.getPort()))
        .forEachRequest(index -> DynamicRequest.forIndex(index)
            .bodyJson("contextId", "ctx-" + index, "data", "value-" + index)
            .header("X-Request-Index", String.valueOf(index))
            .queryParam("batch", String.valueOf(index / 10))
            .tag("originalIndex", index))
        .withConcurrency(5)
        .withTotalRequests(25)
        .execute();

    // Verify custom data is preserved
    assertThat(results.size(), greaterThanOrEqualTo(25));
    results.forEach(result -> {
      assertThat(result.getCustomData(), notNullValue());
      assertThat(result.getCustomData().containsKey("originalIndex"), org.hamcrest.Matchers.is(true));
    });

    log.info("Dynamic context test: {} successful requests with custom data", results.size());
  }

  // ==================== MEMORY EFFICIENCY TESTS ====================

  @Test
  @DisplayName("Should use less memory in lightweight mode")
  void shouldUseLessMemoryInLightweightMode() throws Exception {
    // Setup with large response
    String largeResponse = "{\"data\": \"" + "x".repeat(10000) + "\"}";
    wiremock.stubFor(get(urlPathEqualTo("/api/large"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(largeResponse)));

    // Force GC before test
    System.gc();
    Thread.sleep(100);
    long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    // Execute in lightweight mode
    LoadTestSummary lightweightSummary = TestApi.loadTestLightweight()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/large", wiremock.getPort()))
        .withConcurrency(10)
        .withTotalRequests(100)
        .executeAndSummarize();

    System.gc();
    Thread.sleep(100);
    long memoryAfterLightweight = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    // Verify lightweight results don't store response bodies
    lightweightSummary.getResults().forEach(result -> {
      assertThat("Response body should be null in lightweight mode",
          result.getResponseBody() == null, org.hamcrest.Matchers.is(true));
      assertThat("Response object should be null in lightweight mode",
          result.getResponse() == null, org.hamcrest.Matchers.is(true));
      // But content length should still be tracked
      assertThat(result.getResponseContentLength(), greaterThan(0L));
    });

    log.info("Lightweight mode test: Memory before={} bytes, after={} bytes",
        memoryBefore, memoryAfterLightweight);
    log.info(lightweightSummary.toFormattedString());
  }

  @Test
  @DisplayName("Should truncate response body when maxResponseBodySize is set")
  void shouldTruncateResponseBody() throws Exception {
    // Setup with large response
    String largeResponse = "{\"data\": \"" + "x".repeat(5000) + "\"}";
    wiremock.stubFor(get(urlPathEqualTo("/api/truncate"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(largeResponse)));

    // Execute with max body size - reset first to ensure clean state
    List<ConcurrentRequestResult> results = TestApi.loadTest()
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/truncate", wiremock.getPort()))
        .withMaxResponseBodySize(100) // Only store first 100 bytes
        .withConcurrency(3)
        .withTotalRequests(5)
        .execute();

    // Verify truncation
    results.forEach(result -> {
      assertThat(result.getResponseBody(), notNullValue());
      assertThat(result.getResponseBody().contains("[truncated]"), org.hamcrest.Matchers.is(true));
      assertThat(result.getResponseBody().length(), lessThan(150)); // 100 + "[truncated]"
    });

    log.info("Truncation test: Response bodies truncated to ~100 bytes");
  }

  // ==================== PERFORMANCE BENCHMARK TESTS ====================

  @Test
  @DisplayName("Benchmark: Average throughput measurement")
  void benchmarkAverageThroughput() throws Exception {
    // Setup fast endpoint
    wiremock.stubFor(get(urlPathEqualTo("/api/fast"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"ok\": true}")));

    int totalRequests = 200;
    int concurrency = 20;

    // Execute benchmark
    LoadTestSummary summary = TestApi.loadTestLightweight()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/fast", wiremock.getPort()))
        .withConcurrency(concurrency)
        .withTotalRequests(totalRequests)
        .executeAndSummarize();

    // Report metrics
    log.info("\n╔══════════════════════════════════════════════════════════════╗");
    log.info("║           THROUGHPUT BENCHMARK RESULTS                       ║");
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Configuration:                                               ║");
    log.info("║   Total Requests:    {}                                    ║", String.format("%-5d", totalRequests));
    log.info("║   Concurrency:       {}                                     ║", String.format("%-5d", concurrency));
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Results:                                                     ║");
    log.info("║   Throughput:        {} req/sec                        ║", String.format("%-8.2f", summary.getRequestsPerSecond()));
    log.info("║   Avg Response:      {} ms                              ║", String.format("%-8d", summary.getAverageResponseTime().toMillis()));
    log.info("║   P95 Response:      {} ms                              ║", String.format("%-8d", summary.getP95ResponseTime().toMillis()));
    log.info("║   P99 Response:      {} ms                              ║", String.format("%-8d", summary.getP99ResponseTime().toMillis()));
    log.info("║   Success Rate:      {}%                              ║", String.format("%-8.2f", summary.getSuccessRate()));
    log.info("╚══════════════════════════════════════════════════════════════╝\n");

    // Assertions
    assertThat("Throughput should be positive", summary.getRequestsPerSecond(), greaterThan(0.0));
    assertThat("Success rate should be 100%", summary.getSuccessRate(), greaterThanOrEqualTo(99.0));
  }

  @Test
  @DisplayName("Benchmark: Multiple iterations for average performance")
  void benchmarkMultipleIterations() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/bench"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"benchmark\": true}")));

    int iterations = 5;
    int requestsPerIteration = 100;
    int concurrency = 10;

    double totalThroughput = 0;
    long totalAvgResponseTime = 0;
    double totalSuccessRate = 0;

    log.info("\n╔══════════════════════════════════════════════════════════════╗");
    log.info("║         MULTI-ITERATION BENCHMARK ({} iterations)            ║", iterations);
    log.info("╠══════════════════════════════════════════════════════════════╣");

    for (int i = 0; i < iterations; i++) {
      LoadTestSummary summary = TestApi.loadTestLightweight()
          .reset()
          .withMethod(Method.GET)
          .withUrl(String.format("http://localhost:%d/api/bench", wiremock.getPort()))
          .withConcurrency(concurrency)
          .withTotalRequests(requestsPerIteration)
          .executeAndSummarize();

      totalThroughput += summary.getRequestsPerSecond();
      totalAvgResponseTime += summary.getAverageResponseTime().toMillis();
      totalSuccessRate += summary.getSuccessRate();

      log.info("║ Iteration {}: {} req/s, {} ms avg, {}% success       ║",
          i + 1,
          String.format("%8.2f", summary.getRequestsPerSecond()),
          String.format("%4d", summary.getAverageResponseTime().toMillis()),
          String.format("%6.2f", summary.getSuccessRate()));
    }

    double avgThroughput = totalThroughput / iterations;
    long avgResponseTime = totalAvgResponseTime / iterations;
    double avgSuccessRate = totalSuccessRate / iterations;

    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ AVERAGES ACROSS {} ITERATIONS:                               ║", iterations);
    log.info("║   Average Throughput:     {} req/sec                   ║", String.format("%-8.2f", avgThroughput));
    log.info("║   Average Response Time:  {} ms                         ║", String.format("%-8d", avgResponseTime));
    log.info("║   Average Success Rate:   {}%                         ║", String.format("%-8.2f", avgSuccessRate));
    log.info("╚══════════════════════════════════════════════════════════════╝\n");

    // Assertions
    assertThat("Average throughput should be positive", avgThroughput, greaterThan(0.0));
    assertThat("Average success rate should be high", avgSuccessRate, greaterThanOrEqualTo(99.0));
  }

  @Test
  @DisplayName("Benchmark: Scalability test with increasing concurrency")
  void benchmarkScalability() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/scale"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"scale\": true}")
            .withFixedDelay(10))); // 10ms delay to simulate real API

    int[] concurrencyLevels = {1, 5, 10, 20, 50};
    int requestsPerLevel = 100;

    log.info("\n╔══════════════════════════════════════════════════════════════╗");
    log.info("║              SCALABILITY BENCHMARK                           ║");
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Concurrency │ Throughput │ Avg Resp │ P95 Resp │ Success    ║");
    log.info("╠═════════════╪════════════╪══════════╪══════════╪════════════╣");

    for (int concurrency : concurrencyLevels) {
      LoadTestSummary summary = TestApi.loadTestLightweight()
          .reset()
          .withMethod(Method.GET)
          .withUrl(String.format("http://localhost:%d/api/scale", wiremock.getPort()))
          .withConcurrency(concurrency)
          .withTotalRequests(requestsPerLevel)
          .executeAndSummarize();

      assertThat("Success rate should be high", summary.getSuccessRate(), greaterThanOrEqualTo(99.0));
    }

    log.info("╚══════════════════════════════════════════════════════════════╝\n");
  }

  @Test
  @DisplayName("Benchmark: Rate-limited throughput accuracy")
  void benchmarkRateLimitedThroughput() throws Exception {
    // Setup
    wiremock.stubFor(get(urlPathEqualTo("/api/rate"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"rate\": true}")));

    double targetRps = 20.0;
    Duration duration = Duration.ofSeconds(3);

    LoadTestSummary summary = TestApi.loadTestLightweight()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/rate", wiremock.getPort()))
        .withConcurrency(10)
        .executeWithRate(targetRps, duration);

    double actualRps = summary.getRequestsPerSecond();
    double accuracy = (actualRps / targetRps) * 100;

    log.info("\n╔══════════════════════════════════════════════════════════════╗");
    log.info("║           RATE-LIMITED THROUGHPUT BENCHMARK                  ║");
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Target Rate:       {} req/sec                            ║", String.format("%-8.2f", targetRps));
    log.info("║ Actual Rate:       {} req/sec                            ║", String.format("%-8.2f", actualRps));
    log.info("║ Accuracy:          {}%                                  ║", String.format("%-8.2f", accuracy));
    log.info("║ Duration:          {} seconds                              ║", duration.toSeconds());
    log.info("║ Total Requests:    {}                                     ║", String.format("%-8d", summary.getTotalRequests()));
    log.info("║ Success Rate:      {}%                                  ║", String.format("%-8.2f", summary.getSuccessRate()));
    log.info("╚══════════════════════════════════════════════════════════════╝\n");

    // Rate should be within 20% of target
    assertThat("Rate accuracy should be reasonable", accuracy, greaterThan(80.0));
    assertThat("Rate accuracy should not exceed target by much", accuracy, lessThan(130.0));
  }

  @Test
  @DisplayName("Benchmark: Error handling under load")
  void benchmarkErrorHandlingUnderLoad() throws Exception {
    // Setup with mixed responses (80% success, 20% error)
    wiremock.stubFor(get(urlPathEqualTo("/api/mixed"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"ok\": true}")));

    // Add some error responses
    wiremock.stubFor(get(urlPathEqualTo("/api/error"))
        .willReturn(aResponse()
            .withStatus(500)
            .withBody("{\"error\": \"Internal Server Error\"}")));

    AtomicLong successCount = new AtomicLong(0);
    AtomicLong errorCount = new AtomicLong(0);

    // Execute with callback to track results - using fluent API
    LoadTestSummary summary = TestApi.loadTestLightweight()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/mixed", wiremock.getPort())) // Base URL for validation
        .forEachRequest(index -> DynamicRequest.create()
            // 80% success, 20% error
            .url(String.format("http://localhost:%d%s", wiremock.getPort(),
                (index % 5 == 0) ? "/api/error" : "/api/mixed")))
        .withConcurrency(10)
        .withTotalRequests(100)
        .onEachResult(result -> {
          if (result.isHttpSuccess()) {
            successCount.incrementAndGet();
          } else {
            errorCount.incrementAndGet();
          }
        })
        .executeAndSummarize();

    log.info("\n╔══════════════════════════════════════════════════════════════╗");
    log.info("║           ERROR HANDLING BENCHMARK                           ║");
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Total Requests:    {}                                    ║", String.format("%-8d", summary.getTotalRequests()));
    log.info("║ HTTP 2xx:          {}                                    ║", String.format("%-8d", successCount.get()));
    log.info("║ HTTP 5xx:          {}                                    ║", String.format("%-8d", errorCount.get()));
    log.info("║ Request Success:   {}% (no exceptions)                 ║", String.format("%-8.2f", summary.getSuccessRate()));
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Status Code Distribution:                                    ║");
    summary.getStatusCodeDistribution().forEach((code, count) ->
        log.info("║   HTTP {}: {}                                          ║", code, String.format("%-8d", count)));
    log.info("╚══════════════════════════════════════════════════════════════╝\n");

    // All requests should complete (even with 500 status)
    assertThat("All requests should complete", summary.getSuccessRate(), greaterThanOrEqualTo(99.0));
    // Should have both success and error responses
    assertThat("Should have 2xx responses", successCount.get(), greaterThan(0L));
    assertThat("Should have 5xx responses", errorCount.get(), greaterThan(0L));
  }

  @Test
  @DisplayName("Benchmark: Memory efficiency comparison")
  void benchmarkMemoryEfficiencyComparison() throws Exception {
    // Setup
    String response = "{\"data\": \"" + "x".repeat(1000) + "\"}";
    wiremock.stubFor(get(urlPathEqualTo("/api/memory"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(response)));

    int requests = 500;
    int concurrency = 20;

    // Test 1: Normal mode
    System.gc();
    Thread.sleep(100);
    long memoryBeforeNormal = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    LoadTestSummary normalSummary = TestApi.loadTest()
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/memory", wiremock.getPort()))
        .withConcurrency(concurrency)
        .withTotalRequests(requests)
        .executeAndSummarize();

    System.gc();
    Thread.sleep(100);
    long memoryAfterNormal = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long normalMemoryUsed = memoryAfterNormal - memoryBeforeNormal;

    // Test 2: Lightweight mode
    System.gc();
    Thread.sleep(100);
    long memoryBeforeLightweight = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    LoadTestSummary lightweightSummary = TestApi.loadTestLightweight()
        .reset()
        .withMethod(Method.GET)
        .withUrl(String.format("http://localhost:%d/api/memory", wiremock.getPort()))
        .withConcurrency(concurrency)
        .withTotalRequests(requests)
        .executeAndSummarize();

    System.gc();
    Thread.sleep(100);
    long memoryAfterLightweight = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long lightweightMemoryUsed = memoryAfterLightweight - memoryBeforeLightweight;

    log.info("\n╔══════════════════════════════════════════════════════════════╗");
    log.info("║           MEMORY EFFICIENCY COMPARISON                       ║");
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ Configuration: {} requests, {} concurrency               ║", requests, concurrency);
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ NORMAL MODE:                                                 ║");
    log.info("║   Memory Used:       {} KB                             ║", String.format("%-8d", normalMemoryUsed / 1024));
    log.info("║   Throughput:        {} req/sec                        ║", String.format("%-8.2f", normalSummary.getRequestsPerSecond()));
    log.info("╠══════════════════════════════════════════════════════════════╣");
    log.info("║ LIGHTWEIGHT MODE:                                            ║");
    log.info("║   Memory Used:       {} KB                             ║", String.format("%-8d", lightweightMemoryUsed / 1024));
    log.info("║   Throughput:        {} req/sec                        ║", String.format("%-8.2f", lightweightSummary.getRequestsPerSecond()));
    log.info("╠══════════════════════════════════════════════════════════════╣");
    if (normalMemoryUsed > 0 && lightweightMemoryUsed > 0) {
      double memorySavings = ((double)(normalMemoryUsed - lightweightMemoryUsed) / normalMemoryUsed) * 100;
      log.info("║ Memory Savings:      {}%                               ║", String.format("%-8.2f", memorySavings));
    }
    log.info("╚══════════════════════════════════════════════════════════════╝\n");

    // Both modes should complete successfully
    assertThat(normalSummary.getSuccessRate(), greaterThanOrEqualTo(99.0));
    assertThat(lightweightSummary.getSuccessRate(), greaterThanOrEqualTo(99.0));
  }
}
