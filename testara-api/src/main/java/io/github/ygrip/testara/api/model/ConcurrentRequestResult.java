package io.github.ygrip.testara.api.model;

import io.restassured.response.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Represents the result of a single concurrent request execution.
 *
 * @author yunaz.ramadhan on 1/17/2026
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcurrentRequestResult {

  /**
   * Unique identifier for this request within the batch
   */
  private int requestIndex;

  /**
   * Thread name that executed this request
   */
  private String threadName;

  /**
   * The HTTP response received (null if request failed)
   */
  private Response response;

  /**
   * HTTP status code (0 if request failed before getting response)
   */
  private int statusCode;

  /**
   * Time when the request started
   */
  private Instant startTime;

  /**
   * Time when the request completed
   */
  private Instant endTime;

  /**
   * Total duration of the request
   */
  private Duration duration;

  /**
   * Whether the request was successful (no exceptions thrown)
   */
  private boolean success;

  /**
   * Error message if the request failed
   */
  private String errorMessage;

  /**
   * Exception if the request failed
   */
  private Throwable exception;

  /**
   * Response body as string (for convenience)
   * May be null or truncated in lightweight mode
   */
  private String responseBody;

  /**
   * Custom data passed from RequestContext
   */
  private Map<String, Object> customData;

  /**
   * Response content length in bytes
   */
  private long responseContentLength;

  /**
   * Get duration in milliseconds
   *
   * @return duration in milliseconds
   */
  public long getDurationMillis() {
    return duration != null ? duration.toMillis() : 0;
  }

  /**
   * Check if this result represents a successful HTTP response (2xx status)
   *
   * @return true if status code is 2xx
   */
  public boolean isHttpSuccess() {
    return statusCode >= 200 && statusCode < 300;
  }
}
