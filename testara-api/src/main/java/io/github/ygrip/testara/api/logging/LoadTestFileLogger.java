package io.github.ygrip.testara.api.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.ygrip.testara.api.model.ConcurrentRequestResult;
import io.github.ygrip.testara.api.model.LoadTestSummary;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async file logger for load test requests and responses.
 * Uses virtual threads for efficient non-blocking file I/O.
 * Memory-efficient: writes directly to file without buffering all data in memory.
 *
 * @author yunaz.ramadhan on 1/17/2026
 */
@Log4j2
public class LoadTestFileLogger implements AutoCloseable {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
  private static final ObjectMapper MAPPER = createObjectMapper();

  private final Path outputDir;
  private final String sessionId;
  private final ExecutorService writerExecutor;
  private final ConcurrentLinkedQueue<Object> pendingEntries;
  private final AtomicBoolean closed;
  private final AtomicInteger entryCounter;
  private final boolean enabled;

  // File handles for streaming writes
  private BufferedWriter requestsWriter;
  private BufferedWriter responsesWriter;
  private BufferedWriter errorsWriter;
  private Path requestsFile;
  private Path responsesFile;
  private Path errorsFile;
  private Path summaryFile;

  /**
   * Create a new file logger for load tests.
   *
   * @param testName descriptive name for the test (used in filenames)
   * @param outputDir base output directory (default: target/load-test-logs)
   * @param enabled whether file logging is enabled
   */
  public LoadTestFileLogger(String testName, Path outputDir, boolean enabled) {
    this.enabled = enabled;
    this.sessionId = generateSessionId(testName);
    this.outputDir = outputDir != null ? outputDir : getDefaultOutputDir();
    this.pendingEntries = new ConcurrentLinkedQueue<>();
    this.closed = new AtomicBoolean(false);
    this.entryCounter = new AtomicInteger(0);

    // Use virtual thread per task executor - optimal for I/O bound tasks
    this.writerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    if (enabled) {
      initializeFiles();
    }
  }

  /**
   * Create a file logger with default settings.
   */
  public LoadTestFileLogger(String testName) {
    this(testName, null, true);
  }

  /**
   * Create a disabled file logger (no-op).
   */
  public static LoadTestFileLogger disabled() {
    return new LoadTestFileLogger("disabled", null, false);
  }

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    return mapper;
  }

  private static Path getDefaultOutputDir() {
    return Path.of(System.getProperty("user.dir"), "target", "load-test-logs");
  }

  private String generateSessionId(String testName) {
    String sanitizedName = testName != null ? testName.replaceAll("[^a-zA-Z0-9-_]", "_") : "load-test";
    return sanitizedName + "_" + LocalDateTime.now().format(TIMESTAMP_FORMAT);
  }

  private void initializeFiles() {
    try {
      Path sessionDir = outputDir.resolve(sessionId);
      Files.createDirectories(sessionDir);

      requestsFile = sessionDir.resolve("requests.jsonl");
      responsesFile = sessionDir.resolve("responses.jsonl");
      errorsFile = sessionDir.resolve("errors.jsonl");
      summaryFile = sessionDir.resolve("summary.json");

      // Use buffered writers with append mode for streaming
      requestsWriter = Files.newBufferedWriter(requestsFile, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      responsesWriter = Files.newBufferedWriter(responsesFile, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      errorsWriter = Files.newBufferedWriter(errorsFile, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

      log.info("Load test file logger initialized: {}", sessionDir);
    } catch (IOException e) {
      log.warn("Failed to initialize file logger: {}", e.getMessage());
    }
  }

  /**
   * Log a request asynchronously.
   *
   * @param requestIndex request index
   * @param method HTTP method
   * @param url target URL
   * @param headers request headers
   * @param body request body (can be null)
   */
  public void logRequest(int requestIndex, String method, String url,
                         Map<String, ?> headers, Object body) {
    if (!enabled || closed.get()) return;

    RequestLogEntry entry = RequestLogEntry.builder()
        .index(requestIndex)
        .timestamp(Instant.now())
        .threadName(Thread.currentThread().getName())
        .method(method)
        .url(url)
        .headers(headers)
        .body(body != null ? truncateBody(body.toString(), 10000) : null)
        .build();

    writerExecutor.execute(() -> writeEntry(requestsWriter, entry));
  }

  /**
   * Log a response asynchronously.
   *
   * @param requestIndex request index
   * @param statusCode HTTP status code
   * @param durationMs response time in milliseconds
   * @param headers response headers
   * @param body response body (can be null)
   */
  public void logResponse(int requestIndex, int statusCode, long durationMs,
                          Map<String, ?> headers, String body) {
    if (!enabled || closed.get()) return;

    ResponseLogEntry entry = ResponseLogEntry.builder()
        .index(requestIndex)
        .timestamp(Instant.now())
        .threadName(Thread.currentThread().getName())
        .statusCode(statusCode)
        .durationMs(durationMs)
        .headers(headers)
        .body(truncateBody(body, 10000))
        .build();

    writerExecutor.execute(() -> writeEntry(responsesWriter, entry));
  }

  /**
   * Log an error asynchronously.
   *
   * @param requestIndex request index
   * @param error error message
   * @param exception exception (can be null)
   */
  public void logError(int requestIndex, String error, Throwable exception) {
    if (!enabled || closed.get()) return;

    ErrorLogEntry entry = ErrorLogEntry.builder()
        .index(requestIndex)
        .timestamp(Instant.now())
        .threadName(Thread.currentThread().getName())
        .error(error)
        .exceptionClass(exception != null ? exception.getClass().getName() : null)
        .exceptionMessage(exception != null ? exception.getMessage() : null)
        .build();

    writerExecutor.execute(() -> writeEntry(errorsWriter, entry));
  }

  /**
   * Log a ConcurrentRequestResult asynchronously.
   *
   * @param result the result to log
   */
  public void logResult(ConcurrentRequestResult result) {
    if (!enabled || closed.get() || result == null) return;

    if (result.isSuccess()) {
      ResponseLogEntry entry = ResponseLogEntry.builder()
          .index(result.getRequestIndex())
          .timestamp(result.getEndTime())
          .threadName(result.getThreadName())
          .statusCode(result.getStatusCode())
          .durationMs(result.getDuration().toMillis())
          .body(truncateBody(result.getResponseBody(), 10000))
          .build();
      writerExecutor.execute(() -> writeEntry(responsesWriter, entry));
    } else {
      ErrorLogEntry entry = ErrorLogEntry.builder()
          .index(result.getRequestIndex())
          .timestamp(result.getEndTime())
          .threadName(result.getThreadName())
          .error(result.getErrorMessage())
          .exceptionClass(result.getException() != null ? result.getException().getClass().getName() : null)
          .exceptionMessage(result.getException() != null ? result.getException().getMessage() : null)
          .build();
      writerExecutor.execute(() -> writeEntry(errorsWriter, entry));
    }

    entryCounter.incrementAndGet();
  }

  /**
   * Write the load test summary synchronously.
   *
   * @param summary the summary to write
   */
  public void writeSummary(LoadTestSummary summary) {
    if (!enabled || closed.get() || summary == null) return;

    try {
      String json = MAPPER.writeValueAsString(summary);
      Files.writeString(summaryFile, json, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      log.info("Load test summary written to: {}", summaryFile);
    } catch (IOException e) {
      log.warn("Failed to write summary: {}", e.getMessage());
    }
  }

  /**
   * Write all results at once (for batch mode).
   *
   * @param results list of results
   */
  public void writeAllResults(List<ConcurrentRequestResult> results) {
    if (!enabled || closed.get() || results == null) return;

    for (ConcurrentRequestResult result : results) {
      logResult(result);
    }
  }

  private void writeEntry(BufferedWriter writer, Object entry) {
    if (writer == null || entry == null) return;

    try {
      synchronized (writer) {
        String json = MAPPER.writeValueAsString(entry);
        writer.write(json);
        writer.newLine();
        writer.flush(); // Flush immediately for real-time logging
      }
    } catch (IOException e) {
      log.trace("Failed to write log entry: {}", e.getMessage());
    }
  }

  private String truncateBody(String body, int maxLength) {
    if (body == null) return null;
    if (body.length() <= maxLength) return body;
    return body.substring(0, maxLength) + "...[truncated]";
  }

  /**
   * Get the session directory path.
   */
  public Path getSessionDir() {
    return outputDir.resolve(sessionId);
  }

  /**
   * Get the number of logged entries.
   */
  public int getEntryCount() {
    return entryCounter.get();
  }

  /**
   * Check if logging is enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      // Shutdown executor and wait for pending writes
      writerExecutor.shutdown();
      try {
        if (!writerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          writerExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        writerExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }

      // Close file writers
      closeWriter(requestsWriter);
      closeWriter(responsesWriter);
      closeWriter(errorsWriter);

      if (enabled) {
        log.info("Load test file logger closed. Logged {} entries to: {}", 
            entryCounter.get(), getSessionDir());
      }
    }
  }

  private void closeWriter(BufferedWriter writer) {
    if (writer != null) {
      try {
        writer.close();
      } catch (IOException e) {
        log.trace("Error closing writer: {}", e.getMessage());
      }
    }
  }

  // Inner classes for structured log entries

  @Data
  @Builder
  public static class RequestLogEntry {
    private int index;
    private Instant timestamp;
    private String threadName;
    private String method;
    private String url;
    private Map<String, ?> headers;
    private String body;
  }

  @Data
  @Builder
  public static class ResponseLogEntry {
    private int index;
    private Instant timestamp;
    private String threadName;
    private int statusCode;
    private long durationMs;
    private Map<String, ?> headers;
    private String body;
  }

  @Data
  @Builder
  public static class ErrorLogEntry {
    private int index;
    private Instant timestamp;
    private String threadName;
    private String error;
    private String exceptionClass;
    private String exceptionMessage;
  }
}
