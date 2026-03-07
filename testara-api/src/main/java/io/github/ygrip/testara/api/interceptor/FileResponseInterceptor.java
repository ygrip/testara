package io.github.ygrip.testara.api.interceptor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.model.InterceptorExecutionMode;
import io.github.ygrip.testara.api.model.ResponseLog;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.restassured.response.Response;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async file logging interceptor for responses.
 * Writes response details to files in target/api-logs/ directory.
 * Uses virtual threads for non-blocking I/O.
 *
 * @author yunaz.ramadhan on 1/17/2026
 */
@Log4j2
public final class FileResponseInterceptor implements ResponseInterceptor {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
  private static final ObjectMapper MAPPER = createObjectMapper();
  private static final AtomicInteger RESPONSE_COUNTER = new AtomicInteger(0);
  private static final AtomicReference<BufferedWriter> WRITER_REF = new AtomicReference<>();
  private static final AtomicReference<Path> LOG_FILE_REF = new AtomicReference<>();
  private static volatile String SESSION_ID;

  private final boolean enabled;
  private final int maxBodySize;
  private String serviceName;
  private TestContext context;

  public FileResponseInterceptor() {
    ApiProperties apiConfig = TestFramework.context().configuration().get(ApiProperties.class);
    this.enabled = apiConfig.getEnableFileLogging();
    this.maxBodySize = apiConfig.getFileLoggingMaxBodySize();
    
    if (enabled) {
      initializeSession();
    }
  }

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }

  private synchronized void initializeSession() {
    if (SESSION_ID == null) {
      // Try to use the same session as FileRequestInterceptor
      SESSION_ID = FileRequestInterceptor.getSessionId();
      if (SESSION_ID == null) {
        SESSION_ID = "api_" + LocalDateTime.now().format(TIMESTAMP_FORMAT);
      }
      
      try {
        Path outputDir = Path.of(System.getProperty("user.dir"), "target", "api-logs", SESSION_ID);
        Files.createDirectories(outputDir);
        
        Path logFile = outputDir.resolve("responses.jsonl");
        LOG_FILE_REF.set(logFile);
        
        BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        WRITER_REF.set(writer);
        
        log.info("File response logging initialized: {}", logFile);
        
        // Register shutdown hook to close writer
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          BufferedWriter w = WRITER_REF.get();
          if (w != null) {
            try {
              w.close();
            } catch (IOException ignored) {
            }
          }
        }, "file-response-interceptor-shutdown"));
        
      } catch (IOException e) {
        log.warn("Failed to initialize file response logging: {}", e.getMessage());
      }
    }
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE - 1; // Run before logging interceptor
  }

  @Override
  public ResponseInterceptor service(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  @Override
  public ResponseInterceptor context(TestContext context) {
    this.context = context;
    return this;
  }

  @Override
  public ResponseInterceptor logs(Set<ResponseLog> logLevels) {
    return this;
  }

  @Override
  public InterceptorExecutionMode executionMode() {
    // Run async to not block the response processing
    return InterceptorExecutionMode.ASYNC;
  }

  @Override
  public Duration timeout() {
    return Duration.ofSeconds(2);
  }

  @Override
  public void logic(Response response) {
    if (!enabled || response == null) {
      return;
    }

    try {
      ResponseLogEntry entry = ResponseLogEntry.builder()
          .index(RESPONSE_COUNTER.getAndIncrement())
          .timestamp(Instant.now())
          .threadName(Thread.currentThread().getName())
          .serviceName(serviceName)
          .statusCode(response.getStatusCode())
          .statusLine(response.getStatusLine())
          .contentType(response.getContentType())
          .responseTimeMs(response.getTime())
          .headers(response.getHeaders() != null ? response.getHeaders().asList().stream()
              .collect(java.util.stream.Collectors.toMap(
                  io.restassured.http.Header::getName,
                  io.restassured.http.Header::getValue,
                  (v1, v2) -> v1 + "," + v2))
              : null)
          .cookies(response.getCookies() != null ? response.getDetailedCookies().asList().stream()
              .collect(java.util.stream.Collectors.toMap(
                  io.restassured.http.Cookie::getName,
                  io.restassured.http.Cookie::getValue,
                  (v1, v2) -> v1 + "," + v2))
              : null)
          .body(truncateBody(response))
          .build();

      writeEntry(entry);
      
    } catch (Exception e) {
      log.trace("Error logging response to file: {}", e.getMessage());
    }
  }

  private String truncateBody(Response response) {
    try {
      if (response.getBody() == null) return null;
      String bodyStr = response.getBody().asString();
      if (bodyStr == null) return null;
      if (bodyStr.length() > maxBodySize) {
        return bodyStr.substring(0, maxBodySize) + "...[truncated]";
      }
      return bodyStr;
    } catch (Exception e) {
      return "[Error reading body: " + e.getMessage() + "]";
    }
  }

  private void writeEntry(ResponseLogEntry entry) {
    BufferedWriter writer = WRITER_REF.get();
    if (writer == null) return;

    try {
      synchronized (writer) {
        String json = MAPPER.writeValueAsString(entry);
        writer.write(json);
        writer.newLine();
        writer.flush();
      }
    } catch (IOException e) {
      log.trace("Failed to write response log entry: {}", e.getMessage());
    }
  }

  /**
   * Get the current log file path.
   */
  public static Path getLogFile() {
    return LOG_FILE_REF.get();
  }

  /**
   * Get the current session ID.
   */
  public static String getSessionId() {
    return SESSION_ID;
  }

  @Data
  @Builder
  public static class ResponseLogEntry {
    private int index;
    private Instant timestamp;
    private String threadName;
    private String serviceName;
    private int statusCode;
    private String statusLine;
    private String contentType;
    private long responseTimeMs;
    private Map<String, String> headers;
    private Map<String, String> cookies;
    private String body;
  }
}
