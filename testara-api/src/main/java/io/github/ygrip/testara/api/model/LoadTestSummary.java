package io.github.ygrip.testara.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Summary statistics for a load test execution.
 *
 * @author yunaz.ramadhan on 1/17/2026
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadTestSummary {

  /**
   * Total number of requests executed
   */
  private int totalRequests;

  /**
   * Number of successful requests
   */
  private int successfulRequests;

  /**
   * Number of failed requests
   */
  private int failedRequests;

  /**
   * Time when the load test started
   */
  private Instant startTime;

  /**
   * Time when the load test completed
   */
  private Instant endTime;

  /**
   * Total duration of the load test
   */
  private Duration totalDuration;

  /**
   * Average response time across all requests
   */
  private Duration averageResponseTime;

  /**
   * Minimum response time
   */
  private Duration minResponseTime;

  /**
   * Maximum response time
   */
  private Duration maxResponseTime;

  /**
   * 50th percentile response time (median)
   */
  private Duration p50ResponseTime;

  /**
   * 90th percentile response time
   */
  private Duration p90ResponseTime;

  /**
   * 95th percentile response time
   */
  private Duration p95ResponseTime;

  /**
   * 99th percentile response time
   */
  private Duration p99ResponseTime;

  /**
   * Requests per second (throughput)
   */
  private double requestsPerSecond;

  /**
   * Success rate as percentage (0-100)
   */
  private double successRate;

  /**
   * Individual request results
   */
  @Builder.Default
  private List<ConcurrentRequestResult> results = new ArrayList<>();

  /**
   * Count of responses by status code
   */
  @Builder.Default
  private Map<Integer, Long> statusCodeDistribution = new HashMap<>();

  /**
   * Error messages and their counts
   */
  @Builder.Default
  private Map<String, Long> errorDistribution = new HashMap<>();

  /**
   * Calculate summary from results
   *
   * @param results list of concurrent request results
   * @return computed load test summary
   */
  public static LoadTestSummary fromResults(List<ConcurrentRequestResult> results) {
    if (results == null || results.isEmpty()) {
      return LoadTestSummary.builder()
          .totalRequests(0)
          .successfulRequests(0)
          .failedRequests(0)
          .successRate(0.0)
          .build();
    }

    int total = results.size();
    int successful = (int) results.stream().filter(ConcurrentRequestResult::isSuccess).count();
    int failed = total - successful;

    // Find start and end times
    Instant start = results.stream()
        .map(ConcurrentRequestResult::getStartTime)
        .min(Instant::compareTo)
        .orElse(Instant.now());

    Instant end = results.stream()
        .map(ConcurrentRequestResult::getEndTime)
        .max(Instant::compareTo)
        .orElse(Instant.now());

    Duration totalDuration = Duration.between(start, end);

    // Calculate response time statistics
    List<Long> durations = results.stream()
        .map(ConcurrentRequestResult::getDurationMillis)
        .sorted()
        .collect(Collectors.toList());

    long totalMs = durations.stream().mapToLong(Long::longValue).sum();
    double avgMs = durations.isEmpty() ? 0 : (double) totalMs / durations.size();
    long minMs = durations.isEmpty() ? 0 : durations.getFirst();
    long maxMs = durations.isEmpty() ? 0 : durations.getLast();

    // Calculate percentiles
    Duration p50 = getPercentile(durations, 50);
    Duration p90 = getPercentile(durations, 90);
    Duration p95 = getPercentile(durations, 95);
    Duration p99 = getPercentile(durations, 99);

    // Calculate throughput
    double durationSeconds = totalDuration.toMillis() / 1000.0;
    double rps = durationSeconds > 0 ? total / durationSeconds : 0;

    // Calculate success rate
    double successRate = total > 0 ? (successful * 100.0 / total) : 0;

    // Status code distribution
    Map<Integer, Long> statusCodes = results.stream()
        .filter(r -> r.getStatusCode() > 0)
        .collect(Collectors.groupingBy(ConcurrentRequestResult::getStatusCode, Collectors.counting()));

    // Error distribution
    Map<String, Long> errors = results.stream()
        .filter(r -> !r.isSuccess() && r.getErrorMessage() != null)
        .collect(Collectors.groupingBy(ConcurrentRequestResult::getErrorMessage, Collectors.counting()));

    return LoadTestSummary.builder()
        .totalRequests(total)
        .successfulRequests(successful)
        .failedRequests(failed)
        .startTime(start)
        .endTime(end)
        .totalDuration(totalDuration)
        .averageResponseTime(Duration.ofMillis((long) avgMs))
        .minResponseTime(Duration.ofMillis(minMs))
        .maxResponseTime(Duration.ofMillis(maxMs))
        .p50ResponseTime(p50)
        .p90ResponseTime(p90)
        .p95ResponseTime(p95)
        .p99ResponseTime(p99)
        .requestsPerSecond(rps)
        .successRate(successRate)
        .results(results)
        .statusCodeDistribution(statusCodes)
        .errorDistribution(errors)
        .build();
  }

  private static Duration getPercentile(List<Long> sortedDurations, int percentile) {
    if (sortedDurations.isEmpty()) {
      return Duration.ZERO;
    }
    int index = (int) Math.ceil(percentile / 100.0 * sortedDurations.size()) - 1;
    index = Math.max(0, Math.min(index, sortedDurations.size() - 1));
    return Duration.ofMillis(sortedDurations.get(index));
  }

  /**
   * Get a formatted summary string for logging
   *
   * @return formatted summary string
   */
  public String toFormattedString() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
    sb.append("║                    LOAD TEST SUMMARY                         ║\n");
    sb.append("╠══════════════════════════════════════════════════════════════╣\n");
    sb.append(String.format("║ Total Requests:        %-38d ║%n", totalRequests));
    sb.append(String.format("║ Successful:            %-38d ║%n", successfulRequests));
    sb.append(String.format("║ Failed:                %-38d ║%n", failedRequests));
    sb.append(String.format("║ Success Rate:          %-37.2f%% ║%n", successRate));
    sb.append("╠══════════════════════════════════════════════════════════════╣\n");
    sb.append(String.format("║ Total Duration:        %-35s ms ║%n", totalDuration != null ? totalDuration.toMillis() : "N/A"));
    sb.append(String.format("║ Requests/Second:       %-38.2f ║%n", requestsPerSecond));
    sb.append("╠══════════════════════════════════════════════════════════════╣\n");
    sb.append("║                   RESPONSE TIME METRICS                      ║\n");
    sb.append("╠══════════════════════════════════════════════════════════════╣\n");
    sb.append(String.format("║ Average:               %-35s ms ║%n", averageResponseTime != null ? averageResponseTime.toMillis() : "N/A"));
    sb.append(String.format("║ Min:                   %-35s ms ║%n", minResponseTime != null ? minResponseTime.toMillis() : "N/A"));
    sb.append(String.format("║ Max:                   %-35s ms ║%n", maxResponseTime != null ? maxResponseTime.toMillis() : "N/A"));
    sb.append(String.format("║ P50 (Median):          %-35s ms ║%n", p50ResponseTime != null ? p50ResponseTime.toMillis() : "N/A"));
    sb.append(String.format("║ P90:                   %-35s ms ║%n", p90ResponseTime != null ? p90ResponseTime.toMillis() : "N/A"));
    sb.append(String.format("║ P95:                   %-35s ms ║%n", p95ResponseTime != null ? p95ResponseTime.toMillis() : "N/A"));
    sb.append(String.format("║ P99:                   %-35s ms ║%n", p99ResponseTime != null ? p99ResponseTime.toMillis() : "N/A"));

    if (!statusCodeDistribution.isEmpty()) {
      sb.append("╠══════════════════════════════════════════════════════════════╣\n");
      sb.append("║                   STATUS CODE DISTRIBUTION                   ║\n");
      sb.append("╠══════════════════════════════════════════════════════════════╣\n");
      statusCodeDistribution.forEach((code, count) ->
          sb.append(String.format("║ HTTP %-3d:              %-38d ║%n", code, count)));
    }

    if (!errorDistribution.isEmpty()) {
      sb.append("╠══════════════════════════════════════════════════════════════╣\n");
      sb.append("║                    ERROR DISTRIBUTION                        ║\n");
      sb.append("╠══════════════════════════════════════════════════════════════╣\n");
      errorDistribution.forEach((error, count) -> {
        String truncatedError = error.length() > 35 ? error.substring(0, 32) + "..." : error;
        sb.append(String.format("║ %-35s: %-22d ║%n", truncatedError, count));
      });
    }

    sb.append("╚══════════════════════════════════════════════════════════════╝\n");
    return sb.toString();
  }
}
