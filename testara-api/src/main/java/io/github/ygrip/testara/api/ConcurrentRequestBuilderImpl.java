package io.github.ygrip.testara.api;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.config.ServiceConfig;
import io.github.ygrip.testara.api.config.SharedServiceConfigCache;
import io.github.ygrip.testara.api.logging.LoadTestFileLogger;
import io.github.ygrip.testara.api.model.ApiModel;
import io.github.ygrip.testara.api.model.ConcurrentRequestResult;
import io.github.ygrip.testara.api.model.CreateRequestSpecification;
import io.github.ygrip.testara.api.model.DynamicRequest;
import io.github.ygrip.testara.api.model.LoadTestSummary;
import io.github.ygrip.testara.api.model.ProxyModel;
import io.github.ygrip.testara.api.model.RequestContext;
import io.github.ygrip.testara.api.support.VirtualRestAssured;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.transformer.TransformerService;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.ProxySpecification;
import io.restassured.specification.RequestSpecification;
import lombok.extern.log4j.Log4j2;

/**
 * Implementation of ConcurrentRequestBuilder for load testing with concurrent requests.
 * Uses virtual threads for efficient concurrent execution.
 *
 * @author yunaz.ramadhan on 1/17/2026
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class ConcurrentRequestBuilderImpl implements ConcurrentRequestBuilder, RestApiFacade {

  private static final String DEFAULT_FORMAT = "json";
  private final String REQUEST_FOLDER;

  private final SharedServiceConfigCache sharedServiceConfigCache;
  private final ObjectConverter converter;
  private final Map<String, Object> headers = new HashMap<>();
  private final Map<String, Object> queryParams = new HashMap<>();
  private final Map<String, Object> pathParams = new HashMap<>();
  private final Map<String, Object> formParams = new HashMap<>();
  private final List<Cookie> cookies = new ArrayList<>();
  // Configuration
  private String currentServiceName;
  private int concurrency = 10;
  private int totalRequests = 100;
  private Duration rampUpDuration = Duration.ZERO;
  private Duration requestTimeout = Duration.ofSeconds(30);
  private boolean followRedirects = true;
  // Request configuration
  private Method httpMethod = Method.GET;
  private String url;
  private Object body;
  private ContentType contentType = ContentType.JSON;
  // Customization
  private Function<Integer, Map<String, Object>> requestCustomizer;
  private Consumer<ConcurrentRequestResult> resultCallback;
  private Function<Integer, Object> dynamicBodySupplier;
  private Function<Integer, String> dynamicUrlSupplier;
  private Function<Integer, RequestContext> dynamicContextSupplier;
  private Function<Integer, DynamicRequest> dynamicRequestBuilder;

  // Memory optimization
  private boolean lightweightMode = false;
  private int maxResponseBodySize = -1; // -1 means no limit

  // Specification template
  private CreateRequestSpecification specificationTemplate;

  // Logging configuration
  private LoadTestFileLogger fileLogger;
  private boolean consoleLoggingEnabled = false;
  private boolean logRequests = true;
  private boolean logResponses = true;
  private int logBodyMaxSize = 10000;

  /**
   * Constructor for ConcurrentRequestBuilderImpl.
   *
   * @param sharedServiceConfigCache shared service configuration cache
   */
  public ConcurrentRequestBuilderImpl(SharedServiceConfigCache sharedServiceConfigCache) {
    this.sharedServiceConfigCache = sharedServiceConfigCache;
    this.converter = ObjectConverterLoader.instance();

    String scriptFolder = TestFramework.context()
      .configuration()
      .get(DefaultProperties.class)
      .getScriptFolder();
    this.REQUEST_FOLDER = String.format(
      "%s%s",
      System.getProperty("user.dir"),
      isBlank(scriptFolder) ? "/src/test/resources/" : scriptFolder
    );

    // Initialize logging from properties
    initializeLoggingFromProperties();
  }

  /**
   * Initialize logging configuration from ApiProperties.
   */
  private void initializeLoggingFromProperties() {
    try {
      ApiProperties apiConfig = TestFramework.context()
        .configuration()
        .get(ApiProperties.class);
      if (apiConfig != null) {
        this.consoleLoggingEnabled = Boolean.TRUE.equals(apiConfig.getLoadTestEnableConsoleLogging());
        this.logRequests = Boolean.TRUE.equals(apiConfig.getLoadTestLogRequests());
        this.logResponses = Boolean.TRUE.equals(apiConfig.getLoadTestLogResponses());
        this.logBodyMaxSize =
          apiConfig.getLoadTestLogBodyMaxSize() != null ? apiConfig.getLoadTestLogBodyMaxSize() : 10000;

        // Initialize file logger if enabled in properties
        if (Boolean.TRUE.equals(apiConfig.getLoadTestEnableFileLogging())) {
          String outputDir = apiConfig.getLoadTestLogOutputDir();
          Path outputPath = !isBlank(outputDir) ?
            Path.of(outputDir) :
            Path.of(System.getProperty("user.dir"), "target", "load-test-logs");
          this.fileLogger = new LoadTestFileLogger("load-test", outputPath, true);
          log.debug("Load test file logging enabled from properties");
        }
      }
    } catch (Exception e) {
      log.trace("Could not load logging properties, using defaults: {}", e.getMessage());
    }
  }

  @Override
  public ConcurrentRequestBuilder setService(String serviceName) {
    this.currentServiceName = serviceName;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withConcurrency(int count) {
    this.concurrency = Math.max(1, count);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withTotalRequests(int totalRequests) {
    this.totalRequests = Math.max(1, totalRequests);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withRampUp(Duration duration) {
    this.rampUpDuration = duration != null ? duration : Duration.ZERO;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withMethod(Method method) {
    this.httpMethod = method != null ? method : Method.GET;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withMethod(String method) {
    Method m = CommonHelper.searchEnum(Method.class, method);
    return withMethod(m != null ? m : Method.GET);
  }

  @Override
  public ConcurrentRequestBuilder withUrl(String url) {
    this.url = url;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withBody(Object body) {
    this.body = body;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withContentType(ContentType contentType) {
    this.contentType = contentType != null ? contentType : ContentType.JSON;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withHeaders(Map<String, Object> headers) {
    if (headers != null) {
      this.headers.putAll(headers);
    }
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withHeader(String key, String value) {
    this.headers.put(key, value);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withQueryParams(Map<String, Object> params) {
    if (params != null) {
      this.queryParams.putAll(params);
    }
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withQueryParam(String key, Object value) {
    this.queryParams.put(key, value);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withPathParams(Map<String, Object> params) {
    if (params != null) {
      this.pathParams.putAll(params);
    }
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withPathParam(String key, Object value) {
    this.pathParams.put(key, value);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withFormParams(Map<String, Object> params) {
    if (params != null) {
      this.formParams.putAll(params);
    }
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withCookies(List<Cookie> cookies) {
    if (cookies != null) {
      this.cookies.addAll(cookies);
    }
    return this;
  }

  @Override
  public ConcurrentRequestBuilder fromSpecification(CreateRequestSpecification specification) {
    this.specificationTemplate = specification;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder fromSpecificationPath(String specificationPath) {
    try {
      String fullPath = String.format("%s%s.%s", REQUEST_FOLDER, specificationPath, DEFAULT_FORMAT);
      File file = FileHelper.openFile(fullPath);
      if (file.exists()) {
        this.specificationTemplate = new TransformerService().setTemplate(FileHelper.readFile(fullPath))
          .to(CreateRequestSpecification.class);
      } else {
        log.warn("Specification file not found: {}", fullPath);
      }
    } catch (Exception e) {
      log.error("Error loading specification from path: {}", specificationPath, e);
    }
    return this;
  }

  @Override
  @Deprecated
  public ConcurrentRequestBuilder withRequestCustomizer(Function<Integer, Map<String, Object>> customizer) {
    this.requestCustomizer = customizer;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder onEachResult(Consumer<ConcurrentRequestResult> callback) {
    this.resultCallback = callback;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder forEachRequest(Function<Integer, DynamicRequest> requestBuilder) {
    this.dynamicRequestBuilder = requestBuilder;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withDynamicBody(Function<Integer, Object> bodySupplier) {
    this.dynamicBodySupplier = bodySupplier;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withDynamicUrl(Function<Integer, String> urlSupplier) {
    this.dynamicUrlSupplier = urlSupplier;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withDynamicContext(Function<Integer, RequestContext> contextSupplier) {
    this.dynamicContextSupplier = contextSupplier;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withLightweightMode(boolean lightweight) {
    this.lightweightMode = lightweight;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withMaxResponseBodySize(int maxBytes) {
    this.maxResponseBodySize = maxBytes;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withTimeout(Duration timeout) {
    this.requestTimeout = timeout != null ? timeout : Duration.ofSeconds(30);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder followRedirects(boolean follow) {
    this.followRedirects = follow;
    return this;
  }

  @Override
  public List<ConcurrentRequestResult> execute() throws Exception {
    validateConfiguration();

    log.info("Starting concurrent load test: {} total requests with {} concurrency", totalRequests, concurrency);

    // Use virtual thread per task executor - most efficient for I/O bound tasks
    // Virtual threads are cheap and don't need pooling like platform threads
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // Use semaphore for concurrency control (more efficient than thread pool sizing)
    Semaphore concurrencySemaphore = new Semaphore(concurrency);

    // Use synchronized list for memory efficiency (CopyOnWriteArrayList creates copies on each write)
    List<ConcurrentRequestResult> results = Collections.synchronizedList(new ArrayList<>(totalRequests));
    CountDownLatch completionLatch = new CountDownLatch(totalRequests);
    AtomicInteger requestCounter = new AtomicInteger(0);

    // Calculate ramp-up delay between thread starts (in nanoseconds for precision)
    long rampUpDelayNanos = (rampUpDuration.toNanos()) / Math.max(1, concurrency);

    try {
      List<Future<ConcurrentRequestResult>> futures = new ArrayList<>(totalRequests);

      for (int i = 0; i < totalRequests; i++) {
        final int requestIndex = i;

        // Apply ramp-up delay using LockSupport.parkNanos (virtual thread friendly)
        if (rampUpDelayNanos > 0 && i < concurrency) {
          LockSupport.parkNanos(rampUpDelayNanos);
        }

        Callable<ConcurrentRequestResult> task = () -> {
          // Acquire permit for concurrency control
          concurrencySemaphore.acquire();
          try {
            return executeRequest(requestIndex, requestCounter);
          } finally {
            concurrencySemaphore.release();
          }
        };
        futures.add(executor.submit(task));
      }

      // Collect results
      for (Future<ConcurrentRequestResult> future : futures) {
        try {
          ConcurrentRequestResult result = future.get(requestTimeout.toMillis() * 2, TimeUnit.MILLISECONDS);
          results.add(result);

          // Log to file if enabled
          if (fileLogger != null && fileLogger.isEnabled()) {
            fileLogger.logResult(result);
          }

          if (resultCallback != null) {
            resultCallback.accept(result);
          }
        } catch (Exception e) {
          log.warn("Error collecting result: {}", e.getMessage());
          ConcurrentRequestResult errorResult = ConcurrentRequestResult.builder()
            .requestIndex(requestCounter.get())
            .success(false)
            .errorMessage(e.getMessage())
            .exception(lightweightMode ? null : e)
            .startTime(Instant.now())
            .endTime(Instant.now())
            .duration(Duration.ZERO)
            .build();
          results.add(errorResult);

          if (fileLogger != null && fileLogger.isEnabled()) {
            fileLogger.logResult(errorResult);
          }
        } finally {
          completionLatch.countDown();
        }
      }

      // Wait for all to complete
      completionLatch.await(requestTimeout.toMillis() * totalRequests, TimeUnit.MILLISECONDS);

    } finally {
      shutdownExecutor(executor);
    }

    log.info("Completed load test: {} results collected", results.size());
    return results;
  }

  @Override
  public LoadTestSummary executeAndSummarize() throws Exception {
    List<ConcurrentRequestResult> results = execute();
    LoadTestSummary summary = LoadTestSummary.fromResults(results);
    log.info(summary.toFormattedString());

    // Write summary to file if logging is enabled
    if (fileLogger != null && fileLogger.isEnabled()) {
      fileLogger.writeSummary(summary);
    }

    return summary;
  }

  @Override
  public LoadTestSummary executeWithRate(double requestsPerSecond, Duration duration) throws Exception {
    validateConfiguration();

    log.info("Starting rate-limited load test: {} RPS for {} seconds", requestsPerSecond, duration.toSeconds());

    // Use virtual thread per task executor - optimal for I/O bound operations
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // Semaphore for concurrency control
    Semaphore concurrencySemaphore = new Semaphore(concurrency);

    // Use synchronized list for memory efficiency
    List<ConcurrentRequestResult> results = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger requestCounter = new AtomicInteger(0);

    long intervalNanos = (long) (1_000_000_000.0 / requestsPerSecond);
    long endTimeNanos = System.nanoTime() + duration.toNanos();

    try {
      List<Future<ConcurrentRequestResult>> futures = new ArrayList<>();
      long nextRequestTime = System.nanoTime();

      while (System.nanoTime() < endTimeNanos) {
        // Wait until next scheduled request time using LockSupport (virtual thread friendly)
        long waitNanos = nextRequestTime - System.nanoTime();
        if (waitNanos > 0) {
          // LockSupport.parkNanos is virtual thread friendly - the virtual thread
          // will yield and allow carrier thread to run other virtual threads
          LockSupport.parkNanos(waitNanos);
        }

        final int requestIndex = requestCounter.getAndIncrement();
        Callable<ConcurrentRequestResult> task = () -> {
          concurrencySemaphore.acquire();
          try {
            return executeRequest(requestIndex, requestCounter);
          } finally {
            concurrencySemaphore.release();
          }
        };
        futures.add(executor.submit(task));

        nextRequestTime += intervalNanos;
      }

      // Collect all results
      for (Future<ConcurrentRequestResult> future : futures) {
        try {
          ConcurrentRequestResult result = future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
          results.add(result);

          // Log to file if enabled
          if (fileLogger != null && fileLogger.isEnabled()) {
            fileLogger.logResult(result);
          }

          if (resultCallback != null) {
            resultCallback.accept(result);
          }
        } catch (Exception e) {
          ConcurrentRequestResult errorResult = ConcurrentRequestResult.builder()
            .success(false)
            .errorMessage(e.getMessage())
            .exception(lightweightMode ? null : e)
            .startTime(Instant.now())
            .endTime(Instant.now())
            .duration(Duration.ZERO)
            .build();
          results.add(errorResult);

          if (fileLogger != null && fileLogger.isEnabled()) {
            fileLogger.logResult(errorResult);
          }
        }
      }

    } finally {
      shutdownExecutor(executor);
    }

    LoadTestSummary summary = LoadTestSummary.fromResults(results);
    log.info(summary.toFormattedString());

    // Write summary to file if logging is enabled
    if (fileLogger != null && fileLogger.isEnabled()) {
      fileLogger.writeSummary(summary);
    }

    return summary;
  }

  @Override
  public ConcurrentRequestBuilder reset() {
    // Close existing file logger if any
    if (this.fileLogger != null) {
      try {
        this.fileLogger.close();
      } catch (Exception ignored) {
      }
    }

    this.currentServiceName = null;
    this.concurrency = 10;
    this.totalRequests = 100;
    this.rampUpDuration = Duration.ZERO;
    this.requestTimeout = Duration.ofSeconds(30);
    this.followRedirects = true;
    this.httpMethod = Method.GET;
    this.url = null;
    this.body = null;
    this.contentType = ContentType.JSON;
    this.headers.clear();
    this.queryParams.clear();
    this.pathParams.clear();
    this.formParams.clear();
    this.cookies.clear();
    this.requestCustomizer = null;
    this.resultCallback = null;
    this.dynamicBodySupplier = null;
    this.dynamicUrlSupplier = null;
    this.dynamicContextSupplier = null;
    this.dynamicRequestBuilder = null;
    this.lightweightMode = false;
    this.maxResponseBodySize = -1;
    this.specificationTemplate = null;

    // Reset logging configuration
    this.fileLogger = null;
    this.consoleLoggingEnabled = false;
    this.logRequests = true;
    this.logResponses = true;
    this.logBodyMaxSize = 10000;

    return this;
  }

  @Override
  public RequestSpecification getSpecification() throws Exception {
    return buildSpecification(0, null);
  }

  // ==================== LOGGING IMPLEMENTATION ====================

  @Override
  public ConcurrentRequestBuilder withFileLogging(String testName) {
    return withFileLogging(testName, null);
  }

  @Override
  public ConcurrentRequestBuilder withFileLogging(String testName, Path outputDir) {
    // Close existing logger if any
    if (this.fileLogger != null) {
      try {
        this.fileLogger.close();
      } catch (Exception ignored) {
      }
    }
    this.fileLogger = new LoadTestFileLogger(testName, outputDir, true);
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withFileLogger(LoadTestFileLogger logger) {
    this.fileLogger = logger;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withConsoleLogging(boolean enable) {
    this.consoleLoggingEnabled = enable;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder logRequests(boolean logRequests) {
    this.logRequests = logRequests;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder logResponses(boolean logResponses) {
    this.logResponses = logResponses;
    return this;
  }

  @Override
  public ConcurrentRequestBuilder withLogBodyMaxSize(int maxSize) {
    this.logBodyMaxSize = maxSize;
    return this;
  }

  @Override
  public LoadTestFileLogger getFileLogger() {
    return this.fileLogger;
  }

  private ConcurrentRequestResult executeRequest(int requestIndex, AtomicInteger counter) {
    Instant startTime = Instant.now();
    String threadName = Thread.currentThread()
      .getName();
    RequestContext context = null;
    Map<String, Object> customData = null;
    String targetUrl = null;
    Method method = null;

    try {
      // Get dynamic context - priority: forEachRequest > withDynamicContext
      if (dynamicRequestBuilder != null) {
        DynamicRequest dynamicRequest = dynamicRequestBuilder.apply(requestIndex);
        if (dynamicRequest != null) {
          context = dynamicRequest.build();
          if (context != null) {
            customData = context.getCustomData();
          }
        }
      } else if (dynamicContextSupplier != null) {
        context = dynamicContextSupplier.apply(requestIndex);
        if (context != null) {
          customData = context.getCustomData();
        }
      }

      RequestSpecification specification = buildSpecification(requestIndex, context);

      // Resolve URL (dynamic URL takes precedence)
      targetUrl = resolveUrl(requestIndex, context);

      // Resolve method (context method takes precedence)
      method = resolveMethod(context);

      log.debug("Executing request #{} on thread {}: {} {}", requestIndex, threadName, method, targetUrl);

      Response response = executeHttpRequest(specification, method, targetUrl);

      Instant endTime = Instant.now();
      Duration duration = Duration.between(startTime, endTime);

      // Log response if enabled
      String responseBody = null;
      if (response != null) {
        if (!lightweightMode && (maxResponseBodySize == -1 || response.getBody()
          .asByteArray().length <= maxResponseBodySize)) {
          responseBody = response.getBody()
            .asString();
        }
      }

      // Build result with memory optimization
      ConcurrentRequestResult.ConcurrentRequestResultBuilder resultBuilder = ConcurrentRequestResult.builder()
        .requestIndex(requestIndex)
        .threadName(threadName)
        .statusCode(response != null ? response.getStatusCode() : 0)
        .startTime(startTime)
        .endTime(endTime)
        .duration(duration)
        .success(true)
        .customData(customData);

      // Handle response body based on memory settings
      if (response != null) {
        long contentLength = response.getBody()
          .asByteArray().length;
        resultBuilder.responseContentLength(contentLength);

        if (!lightweightMode) {
          // Store response object only if not in lightweight mode
          resultBuilder.response(response);

          // Store response body with size limit
          if (maxResponseBodySize == -1 || contentLength <= maxResponseBodySize) {
            resultBuilder.responseBody(responseBody != null ?
              responseBody :
              response.getBody()
                .asString());
          } else if (maxResponseBodySize > 0) {
            // Truncate response body
            String fullBody = response.getBody()
              .asString();
            resultBuilder.responseBody(
              fullBody.substring(0, Math.min(fullBody.length(), maxResponseBodySize)) + "...[truncated]");
          }
        }
      }

      return resultBuilder.build();

    } catch (Exception e) {
      Instant endTime = Instant.now();
      Duration duration = Duration.between(startTime, endTime);

      log.warn("Request #{} failed: {}", requestIndex, e.getMessage());

      // Log error
      if (fileLogger != null && fileLogger.isEnabled()) {
        fileLogger.logError(requestIndex, e.getMessage(), lightweightMode ? null : e);
      }
      if (consoleLoggingEnabled) {
        log.error(
          "Request #{} {} {} failed: {}",
          requestIndex,
          method != null ? method.name() : "?",
          targetUrl != null ? targetUrl : "?",
          e.getMessage()
        );
      }

      return ConcurrentRequestResult.builder()
        .requestIndex(requestIndex)
        .threadName(threadName)
        .statusCode(0)
        .startTime(startTime)
        .endTime(endTime)
        .duration(duration)
        .success(false)
        .errorMessage(e.getMessage())
        .exception(lightweightMode ? null : e) // Don't store exception in lightweight mode
        .customData(customData)
        .build();
    }
  }

  private RequestSpecification buildSpecification(int requestIndex, RequestContext context) throws Exception {
    ServiceConfig serviceConfig = null;
    if (!isBlank(currentServiceName)) {
      serviceConfig = sharedServiceConfigCache.getServiceConfig(currentServiceName);
    }

    ApiModel model = serviceConfig != null ? serviceConfig.getApiModel() : null;
    RestAssuredConfig config = createConfig(model);
    RequestSpecBuilder builder = createRequestSpecBuilder(model, config);

    // Set content type (context can override)
    ContentType effectiveContentType =
      (context != null && context.getContentType() != null) ? context.getContentType() : contentType;
    builder.setContentType(effectiveContentType);

    // Add headers (merge base + service + context)
    Map<String, Object> allHeaders = new HashMap<>(this.headers);
    if (serviceConfig != null && serviceConfig.getHeaders() != null) {
      allHeaders.putAll(resolveServiceValues(serviceConfig.getHeaders()));
    }
    if (context != null && context.getHeaders() != null) {
      allHeaders.putAll(context.getHeaders());
    }
    if (!allHeaders.isEmpty()) {
      allHeaders.forEach((key, value) -> builder.addHeader(key, String.valueOf(value)));
    }

    // Add query params (merge base + service + context)
    Map<String, Object> allQueryParams = new HashMap<>(this.queryParams);
    if (serviceConfig != null && serviceConfig.getParameters() != null) {
      allQueryParams.putAll(resolveServiceValues(serviceConfig.getParameters()));
    }
    if (context != null && context.getQueryParams() != null) {
      allQueryParams.putAll(context.getQueryParams());
    }
    if (!allQueryParams.isEmpty()) {
      builder.addQueryParams(allQueryParams);
    }

    // Add path params (merge base + context)
    Map<String, Object> allPathParams = new HashMap<>(this.pathParams);
    if (context != null && context.getPathParams() != null) {
      allPathParams.putAll(context.getPathParams());
    }
    if (!allPathParams.isEmpty()) {
      builder.addPathParams(allPathParams);
    }

    // Add form params (merge base + service + context)
    Map<String, Object> allFormParams = new HashMap<>(this.formParams);
    if (serviceConfig != null && serviceConfig.getFormParams() != null) {
      allFormParams.putAll(resolveServiceValues(serviceConfig.getFormParams()));
    }
    if (context != null && context.getFormParams() != null) {
      allFormParams.putAll(context.getFormParams());
    }
    if (!allFormParams.isEmpty()) {
      builder.addFormParams(allFormParams);
    }

    // Add cookies (base + context)
    for (Cookie cookie : cookies) {
      builder.addCookie(cookie);
    }
    if (context != null && context.getCookies() != null) {
      for (Cookie cookie : context.getCookies()) {
        builder.addCookie(cookie);
      }
    }

    // Set body (priority: context > dynamic supplier > base body)
    Object effectiveBody = resolveBody(requestIndex, context);
    if (effectiveBody != null) {
      Object resolvedBody;
      try {
        resolvedBody = converter.convert(effectiveBody);
      } catch (Exception e) {
        resolvedBody = effectiveBody;
      }
      builder.setBody(resolvedBody);
    }

    // Apply specification template if present
    if (specificationTemplate != null) {
      applySpecificationTemplate(builder, specificationTemplate);
    }

    // Apply per-request customizations (legacy support)
    if (requestCustomizer != null) {
      Map<String, Object> customizations = requestCustomizer.apply(requestIndex);
      if (customizations != null) {
        applyCustomizations(builder, customizations);
      }
    }

    return VirtualRestAssured.given(builder.build());
  }

  private Object resolveBody(int requestIndex, RequestContext context) {
    // Priority: context body > dynamic body supplier > base body
    if (context != null && context.getBody() != null) {
      return context.getBody();
    }
    if (dynamicBodySupplier != null) {
      return dynamicBodySupplier.apply(requestIndex);
    }
    return body;
  }

  private String resolveUrl(int requestIndex, RequestContext context) {
    // Priority: context URL > dynamic URL supplier > specification template > base URL
    if (context != null && !isBlank(context.getUrl())) {
      return context.getUrl();
    }
    if (dynamicUrlSupplier != null) {
      String dynamicUrl = dynamicUrlSupplier.apply(requestIndex);
      if (!isBlank(dynamicUrl)) {
        return dynamicUrl;
      }
    }
    if (specificationTemplate != null && !isBlank(specificationTemplate.getUrl())) {
      return specificationTemplate.getUrl();
    }
    return url != null ? url : "";
  }

  private Method resolveMethod(RequestContext context) {
    // Priority: context method > base method
    if (context != null && context.getMethod() != null) {
      return context.getMethod();
    }
    return httpMethod;
  }

  private RestAssuredConfig createConfig(ApiModel model) {
    RestAssuredConfig config = config();

    if (model != null) {
      config.encoderConfig(RestAssuredConfig.config()
        .getEncoderConfig()
        .appendDefaultContentCharsetToContentTypeIfUndefined(model.isApplyDefaultContentIfUndefined()));
    }

    if (followRedirects) {
      config.redirect(RedirectConfig.redirectConfig()
        .followRedirects(true)
        .maxRedirects(10));
    } else {
      config.redirect(RedirectConfig.redirectConfig()
        .followRedirects(false));
    }

    return config;
  }

  private RequestSpecBuilder createRequestSpecBuilder(ApiModel model, RestAssuredConfig config) {
    RequestSpecBuilder builder = new RequestSpecBuilder().setConfig(config)
      .setRelaxedHTTPSValidation();

    if (model != null) {
      if (!isBlank(model.getHost())) {
        builder.setBaseUri(model.getHost());
      }
      if (!isBlank(model.getBasePath())) {
        builder.setBasePath(model.getBasePath());
      }
      if (model.getPort() != null) {
        builder.setPort(model.getPort());
      }
      if (model.getProxy() != null) {
        ProxyModel proxyModel = model.getProxy();
        ProxySpecification proxy =
          new ProxySpecification(proxyModel.getHost(), proxyModel.getPort(), proxyModel.getScheme());
        if (proxyModel.isWithAuthentication()) {
          proxy.withAuth(proxyModel.getUsername(), proxyModel.getPassword());
        }
        builder.setProxy(proxy);
      }
    }

    return builder;
  }

  private Map<String, Object> resolveServiceValues(ServiceConfig.ParsedConfig parsedConfig) {
    Map<String, Object> result = new HashMap<>();
    if (parsedConfig != null && !parsedConfig.isEmpty()) {
      parsedConfig.getStaticValues()
        .forEach((key, value) -> {
          result.put(key, converter.convert(value));
        });
    }
    return result;
  }

  private void applySpecificationTemplate(RequestSpecBuilder builder, CreateRequestSpecification spec) {
    if (spec.getHeaders() != null) {
      spec.getHeaders()
        .forEach((key, value) -> builder.addHeader(key, String.valueOf(value)));
    }
    if (spec.getQueryParameters() != null) {
      builder.addQueryParams(spec.getQueryParameters());
    }
    if (spec.getPathParameters() != null) {
      builder.addPathParams(spec.getPathParameters());
    }
    if (spec.getFormParameters() != null) {
      builder.addFormParams(spec.getFormParameters());
    }
    if (spec.getPayload() != null) {
      builder.setBody(spec.getPayload());
    }
    if (!isBlank(spec.getContentType())) {
      try {
        builder.setContentType(ContentType.fromContentType(spec.getContentType()));
      } catch (Exception ignored) {
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void applyCustomizations(RequestSpecBuilder builder, Map<String, Object> customizations) {
    if (customizations.containsKey("headers")) {
      Map<String, Object> headers = (Map<String, Object>) customizations.get("headers");
      headers.forEach((key, value) -> builder.addHeader(key, String.valueOf(value)));
    }
    if (customizations.containsKey("queryParams")) {
      builder.addQueryParams((Map<String, ?>) customizations.get("queryParams"));
    }
    if (customizations.containsKey("pathParams")) {
      builder.addPathParams((Map<String, ?>) customizations.get("pathParams"));
    }
    if (customizations.containsKey("body")) {
      builder.setBody(customizations.get("body"));
    }
  }

  private Response executeHttpRequest(RequestSpecification specification, Method method, String url) {
    return switch (method) {
      case POST -> specification.post(url);
      case PUT -> specification.put(url);
      case DELETE -> specification.delete(url);
      case PATCH -> specification.patch(url);
      case HEAD -> specification.head(url);
      case OPTIONS -> specification.options(url);
      case GET -> specification.get(url);
      default -> specification.request(method, url);
    };
  }

  private void validateConfiguration() throws Exception {
    if (isBlank(url) && specificationTemplate == null) {
      throw new Exception("URL must be specified either directly or via specification template");
    }
    if (concurrency < 1) {
      throw new Exception("Concurrency must be at least 1");
    }
    if (totalRequests < 1) {
      throw new Exception("Total requests must be at least 1");
    }
  }

  private void shutdownExecutor(ExecutorService executor) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread()
        .interrupt();
    }

    // Close file logger after executor is shut down
    if (fileLogger != null) {
      try {
        fileLogger.close();
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public String serviceName() {
    return currentServiceName;
  }
}
