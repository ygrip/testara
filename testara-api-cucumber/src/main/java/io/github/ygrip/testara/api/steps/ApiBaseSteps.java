package io.github.ygrip.testara.api.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.api.ConcurrentRequestBuilder;
import io.github.ygrip.testara.api.context.TestApi;
import io.github.ygrip.testara.api.model.CommonResponseModel;
import io.github.ygrip.testara.api.model.CookieModel;
import io.github.ygrip.testara.api.model.DynamicRequest;
import io.github.ygrip.testara.api.model.LoadTestSummary;
import io.github.ygrip.testara.api.model.MultiPartData;
import io.github.ygrip.testara.api.support.CookieHelper;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Cookies;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.http.Method;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;
import org.apache.tika.Tika;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsNull;

import java.io.File;
import java.io.InputStream;
import java.net.HttpCookie;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * @author yunaz.ramadhan on 10/4/2019
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class ApiBaseSteps {
  private final String HTTP_METHODS = "(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|TRACE|CONNECT)";

  @ParameterType(HTTP_METHODS)
  public Method method(String method) {
    return Method.valueOf(method);
  }

  @Inject
  private DataHolder dataHolder;

  // Load test state
  private ConcurrentRequestBuilder loadTestBuilder;
  private LoadTestSummary loadTestSummary;

  @RetryableMethod
  @Given("{actor} using service with alias {word}")
  public void initService(String identifier, String serviceName) throws Throwable {
    TestApi.rest(serviceName);
  }

  @RetryableMethod
  @Given("{actor} prepare headers with data")
  public void prepareHeaders(String identifier, DataTable headers) throws Throwable {
    TestApi.rest().addHeaders(new TransformerService().sourceData(headers.cells()).toMap());
  }

  @RetryableMethod
  @Given("{actor} prepare cookies with data")
  public void prepareCookies(String identifier, DataTable headers) throws Throwable {
    List<CookieModel> cookies = new TransformerService().sourceData(headers.cells()).toList(CookieModel.class);
    for (CookieModel cookie : cookies) {
      TestApi.rest().setCookie(CookieHelper.buildCookie(cookie));
    }
  }

  @RetryableMethod
  @Given("{actor} set cookies from previous response header")
  public void setCookiesFromResponseHeader(String identifier) {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("No previous response data is found", previous != null, equalTo(true));
    Headers headers = previous.getHeaders();
    List<Header> headerList = headers.asList()
        .stream()
        .filter(header -> header.getName().equalsIgnoreCase("set-cookie"))
        .toList();
    for (Header header : headerList) {
      String headerValue = header.getValue();
      if (!isBlank(headerValue)) {
        try {
          List<HttpCookie> cookies = HttpCookie.parse(headerValue);
          if (!cookies.isEmpty()) {
            HttpCookie source = cookies.getFirst();
            Cookie.Builder cookie = new Cookie.Builder(source.getName(), source.getValue());
            cookie.setComment(source.getComment());
            if (!isBlank(source.getDomain())) {
              cookie.setDomain(source.getDomain());
            }
            if (source.getMaxAge() > 0L) {
              long timestamp = TestFramework.context().converter().convert("timestamp()");
              timestamp += source.getMaxAge();
              Date date = new Date(timestamp);
              cookie.setExpiryDate(date);
            }
            if (headerValue.contains("SameSite")) {
              Optional<String> sameSite =
                  Arrays.stream(headerValue.split(";")).filter(key -> key.trim().startsWith("SameSite")).findAny();
              if (sameSite.isPresent()) {
                String[] sameSiteRules = sameSite.get().split("=");
                String rule = sameSiteRules.length > 1 ? sameSiteRules[1] : "";
                cookie.setSameSite(rule);
              }
            }
            cookie.setHttpOnly(source.isHttpOnly());
            cookie.setSecured(source.getSecure());
            cookie.setPath(source.getPath());
            cookie.setVersion(source.getVersion());
            TestApi.rest().setCookie(cookie.build());
          }
        } catch (Exception te) {
          log.warn("#Fail to set cookie , error {}", te.getMessage(), te);
        }
      }
    }
  }

  @RetryableMethod
  @Given("{actor} use previous response cookies")
  public void prepareCookies(String identifier) throws Throwable {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("No previous response data is found", previous != null, equalTo(true));
    Cookies cookies = previous.getCookies();
    TestApi.rest().setCookies(cookies);
  }

  @RetryableMethod
  @Given("{actor} use previous response cookies with name {string}")
  public void prepareCookies(String identifier, String cookieName) throws Throwable {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("No previous response data is found", previous != null, equalTo(true));
    List<Cookie> cookies = previous.getCookies().getList(cookieName);
    for (Cookie cookie : cookies) {
      TestApi.rest().setCookie(cookie);
    }
  }

  @RetryableMethod
  @Given("{actor} use previous response cookie with name {string}")
  public void prepareCookie(String identifier, String cookieName) throws Throwable {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("No previous response data is found", previous != null, equalTo(true));
    Cookie cookie = previous.getCookies().get(cookieName);
    TestApi.rest().setCookie(cookie);
  }

  @RetryableMethod
  @Given("{actor} prepare header {string} with value {string}")
  public void addHeaderSteps(String identifier, String key, String value) throws Throwable {
    TestApi.rest().addHeader(key, value);
  }

  @RetryableMethod
  @Given("{actor} prepare pathParam for {word} with value {string}")
  public void setPathParamSteps(String identifier, String key, String value) throws Throwable {
    TestApi.rest().setPathParam(key, value);
  }

  @RetryableMethod
  @Given("{actor} prepare formParams with data")
  public void prepareFormParamWithData(String identifier, DataTable formParams) throws Throwable {
    TestApi.rest().addFormParams(new TransformerService().sourceData(formParams.cells()).toMap());
  }

  @RetryableMethod
  @Given("{actor} prepare multiPart data for {word} with value {string}")
  public void prepareMultiPartWithData(String identifier, String key, String value) throws Throwable {
    TestApi.rest().setMultiPartData(key, value);
  }

  @RetryableMethod
  @Given("{actor} prepare multiPart data with value :")
  public void prepareMultiPartWithData(String identifier, DataTable table) throws Throwable {
    List<MultiPartData> multiPartSpecBuilders =
        new TransformerService().sourceData(table.cells()).to(new TypeReference<>() {
        });
    for (MultiPartData multiPartSpecBuilder : multiPartSpecBuilders) {
      Object content = multiPartSpecBuilder.getContent();
      if (!isBlank(content)) {
        try {
          String controlName;
          if (!isBlank(multiPartSpecBuilder.getControlName())) {
            controlName = multiPartSpecBuilder.getControlName();
          } else {
            controlName = "data";
            try {
              File file = new File(content.toString());
              if (file.exists()) {
                if (file.isFile()) {
                  controlName = "file";
                }
              }
            } catch (Exception ignored) {

            }
          }
          MultiPartSpecBuilder builder = null;
          if (controlName.trim().toLowerCase().startsWith("file")) {
            builder = new MultiPartSpecBuilder(new File(content.toString()));
          } else {
            builder = new MultiPartSpecBuilder(content);
          }
          new MultiPartSpecBuilder(content);
          builder.controlName(controlName);
          if (!isBlank(multiPartSpecBuilder.getCharset())) {
            builder.charset(multiPartSpecBuilder.getCharset());
          }
          if (!isBlank(multiPartSpecBuilder.getMimeType())) {
            builder.mimeType(multiPartSpecBuilder.getMimeType());
          } else {
            String mimeType = "application/octet-stream";
            switch (content) {
              case File file -> {
                Tika tika = new Tika();
                mimeType = tika.detect(file);
              }
              case InputStream inputStream -> {
                Tika tika = new Tika();
                mimeType = tika.detect(inputStream);
              }
              case String s -> {
                Tika tika = new Tika();
                mimeType = tika.detect(s);
              }
              default -> {
              }
            }
            builder.mimeType(mimeType);
          }
          if (!isBlank(multiPartSpecBuilder.getHeaders())) {
            builder.headers(multiPartSpecBuilder.getHeaders());
          }
          if (!isBlank(multiPartSpecBuilder.getFileName())) {
            builder.fileName(multiPartSpecBuilder.getFileName());
          }
          TestApi.rest().setMultiPartData(builder.build());
        } catch (Exception ignored) {
        }
      }
    }
  }

  @RetryableMethod
  @Given("{actor} prepare queryParams with data")
  public void prepareQueryParams(String identifier, DataTable queryParams) throws Throwable {
    TestApi.rest().addQueryParams(new TransformerService().sourceData(queryParams.cells()).toMap());
  }

  @RetryableMethod
  @Given("{actor} prepare body request with value {string}")
  public void setBodyRequestSteps(String identifier, String value) throws Throwable {
    TestApi.rest().setBody(value);
  }

  @RetryableMethod
  @Given("{actor} prepare queryParam {string} with value {string}")
  public void addQueryParamSteps(String identifier, String key, String value) throws Throwable {
    TestApi.rest().addQueryParam(key, value);
  }

  @RetryableMethod
  @When("{actor} process request to {string}")
  public void whenTryMakeApiCall(String identifier, String requestSpecificationPath) throws Throwable {
    TestApi.rest().process(requestSpecificationPath);
  }

  @RetryableMethod
  @When("{actor} try {httpMethod} request to {string}")
  public void whenHitEndPointWithoutParameter(String identifier, String httpMethodStr, String url) throws Throwable {
    Method httpMethod = Method.valueOf(httpMethodStr);
    TestApi.rest().process(httpMethod, TestFramework.context().converter().convert(url));
  }

  @RetryableMethod
  @When("{actor} try {httpMethod} request to {string} with parameter")
  public void whenHitEndPointWithParameter(String identifier, String httpMethodStr, String url, DataTable parameter)
      throws Throwable {
    Method httpMethod = Method.valueOf(httpMethodStr);
    TestApi.rest()
        .addQueryParams(new TransformerService().sourceData(parameter.cells()).toMap())
        .process(httpMethod, TestFramework.context().converter().convert(url));
  }

  @RetryableMethod
  @When("{actor} try {httpMethod} request to {string} with parameter and download file on location {string}")
  public void whenHitEndPointWithParameterAndDownloadFile(String identifier,
      String httpMethodStr,
      String url,
      String path,
      DataTable parameter) throws Throwable {
    Method httpMethod = Method.valueOf(httpMethodStr);
    Response response = TestApi.rest()
        .addQueryParams(new TransformerService().sourceData(parameter.cells()).toMap())
        .buildSpecification()
        .request(httpMethod, url);
    //String downloadFileName = response.getHeader("Content-Disposition").split("=")[1].replace("\"", "");
    String current = System.getProperty("user.dir");
    String fullPath =
        FileHelper.writeFile(response.getBody().asInputStream(), current + path, StandardCopyOption.REPLACE_EXISTING);
    log.info("File Saved Successfully on: {}", fullPath);

    assertThat(fullPath, is(IsNull.notNullValue()));
  }

  @RetryableMethod
  @When("{actor} try {httpMethod} request to {string} and download file on location {string}")
  public void whenHitEndPointAndDownloadFile(String identifier, String httpMethodStr, String url, String path)
      throws Throwable {
    Method httpMethod = Method.valueOf(httpMethodStr);
    Response response = TestApi.rest().buildSpecification().request(httpMethod, url);
    //String downloadFileName = response.getHeader("Content-Disposition").split("=")[1].replace("\"", "");
    String current = System.getProperty("user.dir");
    String fullPath =
        FileHelper.writeFile(response.getBody().asInputStream(), current + path, StandardCopyOption.REPLACE_EXISTING);
    log.info("File Saved Successfully on: {}", fullPath);

    assertThat(fullPath, is(IsNull.notNullValue()));
  }

  @RetryableMethod
  @Then("{actor} assign previous response data to {word}")
  public void assignResponseData(String identifier, String key) throws Throwable {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("latest response is empty", previous, notNullValue());
    dataHolder.setResponse(key, previous.getBody());
  }

  @RetryableMethod
  @Then("{actor} assign previous response cookies to {word}")
  public void assignResponseCookies(String identifier, String key) throws Throwable {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("latest response is empty", previous, notNullValue());
    dataHolder.setResponse(key, previous.getCookies());
  }

  @RetryableMethod
  @Then("{actor} assign previous response headers to {word}")
  public void assignResponseHeaders(String identifier, String key) throws Throwable {
    CommonResponseModel previous = TestApi.response().getData();
    assertThat("latest response is empty", previous, notNullValue());
    dataHolder.setResponse(key, previous.getHeaders());
  }

  @RetryableMethod
  @Then("{actor} response statusCode should be {int}")
  public void statusCodeShouldBe(String identifier, Integer statusCode) {
    assertThat(String.format("response status code is not %s", statusCode),
        TestApi.response().getStatusCode(),
        equalTo(statusCode));
  }

  @RetryableMethod
  @Then("{actor} response success should be {word}")
  public void successShouldBe(String identifier, Boolean isSuccess) {
    assertThat(String.format("response success is not %s", isSuccess),
        TestApi.response().isSuccess(),
        equalTo(isSuccess));
  }

  @RetryableMethod
  @Then("{actor} response errorCode should be {string}")
  public void errorCodeShouldBe(String identifier, String errorCode) {
    errorCode = TestFramework.context().converter().convert(errorCode);
    assertThat(String.format("response errorCode is not %s", errorCode),
        TestApi.response().getErrorCode(),
        equalTo(errorCode));
  }

  @RetryableMethod
  @Then("{actor} response errorMessage should be {string}")
  public void errorMessageShouldBe(String identifier, String errorMessage) {
    errorMessage = TestFramework.context().converter().convert(errorMessage);
    assertThat(String.format("response errorMessage code is not %s", errorMessage),
        TestApi.response().getErrorMessage(),
        equalTo(errorMessage));
  }

  @RetryableMethod
  @Then("{actor} response statusCode should be {int} and success should be {bool}")
  public void statusCodeAndSuccessShouldeBe(String identifier, Integer statusCode, Boolean isSuccess) {
    statusCodeShouldBe(identifier, statusCode);
    successShouldBe(identifier, isSuccess);
  }

  @RetryableMethod
  @Then("{actor} response errorCode should be {string} and errorMessage should be {string}")
  public void errorCodeAndErrorMessageShouldBe(String identifier, String errorCode, String errorMessage) {
    errorCodeShouldBe(identifier, errorCode);
    errorMessageShouldBe(identifier, errorMessage);
  }

  @Then("{actor} measured response time should be less than {long} milliseconds")
  public void measuredResponseTimeShouldBeLessThanMillis(String identifier, long expectedMaxTime) throws Throwable {
    CommonResponseModel lastResponse = TestApi.response().getData();
    assertThat("No measured response time found.", lastResponse, is(Matchers.notNullValue()));

    long actualResponseTime = lastResponse.getResponseTimeMillis();
    assertThat(String.format("Response time %d ms is not less than %d ms", actualResponseTime, expectedMaxTime),
        actualResponseTime < expectedMaxTime,
        equalTo(true));
  }

  @Then("{actor} measured response time should be less than equal {long} milliseconds")
  public void measuredResponseTimeShouldBeLessThanEqualMillis(String identifier, long expectedMaxTime)
      throws Throwable {
    CommonResponseModel lastResponse = TestApi.response().getData();
    assertThat("No measured response time found.", lastResponse, is(Matchers.notNullValue()));

    long actualResponseTime = lastResponse.getResponseTimeMillis();
    assertThat(String.format("Response time %d ms is not less than %d ms", actualResponseTime, expectedMaxTime),
        actualResponseTime <= expectedMaxTime,
        equalTo(true));
  }

  @Then("{actor} measured response time should be between {long} and {long} milliseconds")
  public void measuredResponseTimeShouldBeBetweenMillis(String identifier, long minTime, long maxTime)
      throws Throwable {
    CommonResponseModel lastResponse = TestApi.response().getData();
    assertThat("No measured response time found.", lastResponse, is(Matchers.notNullValue()));

    long actualResponseTime = lastResponse.getResponseTimeMillis();
    assertThat(String.format("Response time %d ms is not between %d ms and %d ms",
        actualResponseTime,
        minTime,
        maxTime), actualResponseTime >= minTime && actualResponseTime <= maxTime, equalTo(true));
  }

  // ==================== LOAD TEST STEPS ====================

  /**
   * Initialize a new load test builder
   */
  @Given("{actor} prepare load test")
  public void prepareLoadTest(String identifier) {
    this.loadTestBuilder = TestApi.loadTest().reset();
    this.loadTestSummary = null;
  }

  /**
   * Initialize a new load test builder with service configuration
   */
  @Given("{actor} prepare load test using service {word}")
  public void prepareLoadTestWithService(String identifier, String serviceName) {
    this.loadTestBuilder = TestApi.loadTest(serviceName).reset();
    this.loadTestSummary = null;
  }

  /**
   * Initialize a lightweight load test (memory efficient)
   */
  @Given("{actor} prepare lightweight load test")
  public void prepareLightweightLoadTest(String identifier) {
    this.loadTestBuilder = TestApi.loadTestLightweight().reset();
    this.loadTestSummary = null;
  }

  /**
   * Initialize a lightweight load test with service configuration
   */
  @Given("{actor} prepare lightweight load test using service {word}")
  public void prepareLightweightLoadTestWithService(String identifier, String serviceName) {
    this.loadTestBuilder = TestApi.loadTestLightweight(serviceName).reset();
    this.loadTestSummary = null;
  }

  // ==================== LOAD TEST CONFIGURATION ====================

  /**
   * Set the number of concurrent threads
   */
  @Given("{actor} set load test concurrency to {int}")
  public void setLoadTestConcurrency(String identifier, int concurrency) {
    assertLoadTestInitialized();
    loadTestBuilder.withConcurrency(concurrency);
  }

  /**
   * Set the total number of requests
   */
  @Given("{actor} set load test total requests to {int}")
  public void setLoadTestTotalRequests(String identifier, int totalRequests) {
    assertLoadTestInitialized();
    loadTestBuilder.withTotalRequests(totalRequests);
  }

  /**
   * Set concurrency and total requests together
   */
  @Given("{actor} set load test with {int} concurrent users and {int} total requests")
  public void setLoadTestConcurrencyAndRequests(String identifier, int concurrency, int totalRequests) {
    assertLoadTestInitialized();
    loadTestBuilder.withConcurrency(concurrency).withTotalRequests(totalRequests);
  }

  /**
   * Set ramp-up duration in seconds
   */
  @Given("{actor} set load test ramp-up to {int} seconds")
  public void setLoadTestRampUp(String identifier, int seconds) {
    assertLoadTestInitialized();
    loadTestBuilder.withRampUp(Duration.ofSeconds(seconds));
  }

  /**
   * Set request timeout in seconds
   */
  @Given("{actor} set load test timeout to {int} seconds")
  public void setLoadTestTimeout(String identifier, int seconds) {
    assertLoadTestInitialized();
    loadTestBuilder.withTimeout(Duration.ofSeconds(seconds));
  }

  /**
   * Enable or disable following redirects
   */
  @Given("{actor} set load test follow redirects to {word}")
  public void setLoadTestFollowRedirects(String identifier, boolean followRedirects) {
    assertLoadTestInitialized();
    loadTestBuilder.followRedirects(followRedirects);
  }

  /**
   * Enable lightweight mode for memory efficiency
   */
  @Given("{actor} enable load test lightweight mode")
  public void enableLoadTestLightweightMode(String identifier) {
    assertLoadTestInitialized();
    loadTestBuilder.withLightweightMode(true);
  }

  /**
   * Set maximum response body size to store (bytes)
   */
  @Given("{actor} set load test max response body size to {int} bytes")
  public void setLoadTestMaxResponseBodySize(String identifier, int maxBytes) {
    assertLoadTestInitialized();
    loadTestBuilder.withMaxResponseBodySize(maxBytes);
  }

  // ==================== LOAD TEST REQUEST CONFIGURATION ====================

  /**
   * Set the HTTP method for load test
   */
  @Given("{actor} set load test method to {httpMethod}")
  public void setLoadTestMethod(String identifier, String methodStr) {
    Method method = Method.valueOf(methodStr);
    assertLoadTestInitialized();
    loadTestBuilder.withMethod(method);
  }

  /**
   * Set the URL for load test
   */
  @Given("{actor} set load test URL to {string}")
  public void setLoadTestUrl(String identifier, String url) {
    assertLoadTestInitialized();
    String resolvedUrl = TestFramework.context().converter().convert(url);
    loadTestBuilder.withUrl(resolvedUrl);
  }

  /**
   * Set the content type for load test
   */
  @Given("{actor} set load test content type to {word}")
  public void setLoadTestContentType(String identifier, String contentType) {
    assertLoadTestInitialized();
    ContentType type = ContentType.fromContentType(contentType);
    loadTestBuilder.withContentType(type != null ? type : ContentType.JSON);
  }

  /**
   * Set the request body for load test
   */
  @Given("{actor} set load test body to {string}")
  public void setLoadTestBody(String identifier, String body) {
    assertLoadTestInitialized();
    Object resolvedBody = TestFramework.context().converter().convert(body);
    loadTestBuilder.withBody(resolvedBody);
  }

  /**
   * Set the request body for load test (multiline)
   */
  @Given("{actor} set load test body to :")
  public void setLoadTestBodyMultiline(String identifier, String body) {
    assertLoadTestInitialized();
    Object resolvedBody = TestFramework.context().converter().convert(body);
    loadTestBuilder.withBody(resolvedBody);
  }

  /**
   * Add headers to load test from DataTable
   */
  @Given("{actor} set load test headers with data")
  public void setLoadTestHeaders(String identifier, DataTable headers) {
    assertLoadTestInitialized();
    Map<String, Object> headerMap = new TransformerService().sourceData(headers.cells()).toMap();
    loadTestBuilder.withHeaders(headerMap);
  }

  /**
   * Add a single header to load test
   */
  @Given("{actor} set load test header {string} to {string}")
  public void setLoadTestHeader(String identifier, String key, String value) {
    assertLoadTestInitialized();
    String resolvedValue = TestFramework.context().converter().convert(value);
    loadTestBuilder.withHeader(key, resolvedValue);
  }

  /**
   * Add query parameters to load test from DataTable
   */
  @Given("{actor} set load test query params with data")
  public void setLoadTestQueryParams(String identifier, DataTable params) {
    assertLoadTestInitialized();
    Map<String, Object> paramMap = new TransformerService().sourceData(params.cells()).toMap();
    loadTestBuilder.withQueryParams(paramMap);
  }

  /**
   * Add a single query parameter to load test
   */
  @Given("{actor} set load test query param {string} to {string}")
  public void setLoadTestQueryParam(String identifier, String key, String value) {
    assertLoadTestInitialized();
    Object resolvedValue = TestFramework.context().converter().convert(value);
    loadTestBuilder.withQueryParam(key, resolvedValue);
  }

  /**
   * Add path parameters to load test from DataTable
   */
  @Given("{actor} set load test path params with data")
  public void setLoadTestPathParams(String identifier, DataTable params) {
    assertLoadTestInitialized();
    Map<String, Object> paramMap = new TransformerService().sourceData(params.cells()).toMap();
    loadTestBuilder.withPathParams(paramMap);
  }

  /**
   * Add a single path parameter to load test
   */
  @Given("{actor} set load test path param {string} to {string}")
  public void setLoadTestPathParam(String identifier, String key, String value) {
    assertLoadTestInitialized();
    Object resolvedValue = TestFramework.context().converter().convert(value);
    loadTestBuilder.withPathParam(key, resolvedValue);
  }

  /**
   * Add form parameters to load test from DataTable
   */
  @Given("{actor} set load test form params with data")
  public void setLoadTestFormParams(String identifier, DataTable params) {
    assertLoadTestInitialized();
    Map<String, Object> paramMap = new TransformerService().sourceData(params.cells()).toMap();
    loadTestBuilder.withFormParams(paramMap);
  }

  // ==================== DYNAMIC REQUEST CONFIGURATION ====================

  /**
   * Configure dynamic unique ID per request (adds X-Request-ID header)
   */
  @Given("{actor} set load test with unique request ID per request")
  public void setLoadTestUniqueRequestId(String identifier) {
    assertLoadTestInitialized();
    loadTestBuilder.forEachRequest(index -> DynamicRequest.create()
        .requestId(UUID.randomUUID().toString()));
  }

  /**
   * Configure dynamic pagination per request
   */
  @Given("{actor} set load test with pagination starting from page {int} with size {int}")
  public void setLoadTestWithPagination(String identifier, int startPage, int pageSize) {
    assertLoadTestInitialized();
    loadTestBuilder.forEachRequest(index -> DynamicRequest.create()
        .paginate(startPage + index, pageSize));
  }

  /**
   * Configure dynamic query param 'id' per request with modulo rotation
   */
  @Given("{actor} set load test with dynamic ID rotating through {int} values")
  public void setLoadTestWithDynamicIdRotation(String identifier, int rotationCount) {
    assertLoadTestInitialized();
    loadTestBuilder.forEachRequest(index -> DynamicRequest.create()
        .queryParam("id", index % rotationCount));
  }

  /**
   * Configure dynamic body with index-based variation
   */
  @Given("{actor} set load test with dynamic body field {string}")
  public void setLoadTestWithDynamicBody(String identifier, String fieldName) {
    assertLoadTestInitialized();
    loadTestBuilder.forEachRequest(index -> DynamicRequest.create()
        .bodyJson(fieldName, "value-" + index));
  }

  /**
   * Configure dynamic header with index-based variation
   */
  @Given("{actor} set load test with dynamic header {string}")
  public void setLoadTestWithDynamicHeader(String identifier, String headerName) {
    assertLoadTestInitialized();
    loadTestBuilder.forEachRequest(index -> DynamicRequest.create()
        .header(headerName, "value-" + index));
  }

  /**
   * Configure dynamic query param with index-based variation
   */
  @Given("{actor} set load test with dynamic query param {string}")
  public void setLoadTestWithDynamicQueryParam(String identifier, String paramName) {
    assertLoadTestInitialized();
    loadTestBuilder.forEachRequest(index -> DynamicRequest.create()
        .queryParam(paramName, "value-" + index));
  }

  // ==================== LOAD TEST EXECUTION ====================

  /**
   * Execute the load test
   */
  @When("{actor} execute load test")
  public void executeLoadTest(String identifier) throws Exception {
    assertLoadTestInitialized();
    this.loadTestSummary = loadTestBuilder.executeAndSummarize();
    log.info("Load test executed: {} total requests", loadTestSummary.getTotalRequests());
    log.info(loadTestSummary.toFormattedString());
  }

  /**
   * Execute load test with rate limiting
   */
  @When("{actor} execute load test with (\\d+\\.?\\d*) requests per second for {int} seconds")
  public void executeLoadTestWithRate(String identifier, double rps, int durationSeconds) throws Exception {
    assertLoadTestInitialized();
    this.loadTestSummary = loadTestBuilder.executeWithRate(rps, Duration.ofSeconds(durationSeconds));
    log.info("Rate-limited load test executed: {} total requests at {} RPS",
        loadTestSummary.getTotalRequests(), rps);
    log.info(loadTestSummary.toFormattedString());
  }

  /**
   * Store load test summary to data holder
   */
  @Then("{actor} assign load test summary to {word}")
  public void assignLoadTestSummary(String identifier, String key) {
    assertLoadTestCompleted();
    dataHolder.setResponse(key, loadTestSummary);
  }

  // ==================== LOAD TEST ASSERTIONS ====================

  /**
   * Assert total requests count
   */
  @Then("{actor} load test total requests should be {int}")
  public void loadTestTotalRequestsShouldBe(String identifier, int expected) {
    assertLoadTestCompleted();
    assertThat("Total requests mismatch",
        loadTestSummary.getTotalRequests(), equalTo(expected));
  }

  /**
   * Assert successful requests count
   */
  @Then("{actor} load test successful requests should be {int}")
  public void loadTestSuccessfulRequestsShouldBe(String identifier, int expected) {
    assertLoadTestCompleted();
    assertThat("Successful requests mismatch",
        loadTestSummary.getSuccessfulRequests(), equalTo(expected));
  }

  /**
   * Assert failed requests count
   */
  @Then("{actor} load test failed requests should be {int}")
  public void loadTestFailedRequestsShouldBe(String identifier, int expected) {
    assertLoadTestCompleted();
    assertThat("Failed requests mismatch",
        loadTestSummary.getFailedRequests(), equalTo(expected));
  }

  /**
   * Assert success rate is at least a certain percentage
   */
  @Then("{actor} load test success rate should be at least (\\d+\\.?\\d*) percent")
  public void loadTestSuccessRateShouldBeAtLeast(String identifier, double minSuccessRate) {
    assertLoadTestCompleted();
    assertThat(String.format("Success rate %.2f%% is below minimum %.2f%%",
            loadTestSummary.getSuccessRate(), minSuccessRate),
        loadTestSummary.getSuccessRate(), greaterThanOrEqualTo(minSuccessRate));
  }

  /**
   * Assert success rate equals specific value
   */
  @Then("{actor} load test success rate should be (\\d+\\.?\\d*) percent")
  public void loadTestSuccessRateShouldBe(String identifier, double expectedRate) {
    assertLoadTestCompleted();
    assertThat(String.format("Success rate %.2f%% does not match expected %.2f%%",
            loadTestSummary.getSuccessRate(), expectedRate),
        loadTestSummary.getSuccessRate(), equalTo(expectedRate));
  }

  /**
   * Assert throughput is at least a certain value
   */
  @Then("{actor} load test throughput should be at least (\\d+\\.?\\d*) requests per second")
  public void loadTestThroughputShouldBeAtLeast(String identifier, double minThroughput) {
    assertLoadTestCompleted();
    assertThat(String.format("Throughput %.2f req/s is below minimum %.2f req/s",
            loadTestSummary.getRequestsPerSecond(), minThroughput),
        loadTestSummary.getRequestsPerSecond(), greaterThanOrEqualTo(minThroughput));
  }

  /**
   * Assert average response time is at most a certain value
   */
  @Then("{actor} load test average response time should be at most {long} milliseconds")
  public void loadTestAvgResponseTimeShouldBeAtMost(String identifier, long maxMillis) {
    assertLoadTestCompleted();
    long actualAvg = loadTestSummary.getAverageResponseTime().toMillis();
    assertThat(String.format("Average response time %d ms exceeds maximum %d ms", actualAvg, maxMillis),
        actualAvg, lessThanOrEqualTo(maxMillis));
  }

  /**
   * Assert P50 response time is at most a certain value
   */
  @Then("{actor} load test P50 response time should be at most {long} milliseconds")
  public void loadTestP50ResponseTimeShouldBeAtMost(String identifier, long maxMillis) {
    assertLoadTestCompleted();
    long actualP50 = loadTestSummary.getP50ResponseTime().toMillis();
    assertThat(String.format("P50 response time %d ms exceeds maximum %d ms", actualP50, maxMillis),
        actualP50, lessThanOrEqualTo(maxMillis));
  }

  /**
   * Assert P90 response time is at most a certain value
   */
  @Then("{actor} load test P90 response time should be at most {long} milliseconds")
  public void loadTestP90ResponseTimeShouldBeAtMost(String identifier, long maxMillis) {
    assertLoadTestCompleted();
    long actualP90 = loadTestSummary.getP90ResponseTime().toMillis();
    assertThat(String.format("P90 response time %d ms exceeds maximum %d ms", actualP90, maxMillis),
        actualP90, lessThanOrEqualTo(maxMillis));
  }

  /**
   * Assert P95 response time is at most a certain value
   */
  @Then("{actor} load test P95 response time should be at most {long} milliseconds")
  public void loadTestP95ResponseTimeShouldBeAtMost(String identifier, long maxMillis) {
    assertLoadTestCompleted();
    long actualP95 = loadTestSummary.getP95ResponseTime().toMillis();
    assertThat(String.format("P95 response time %d ms exceeds maximum %d ms", actualP95, maxMillis),
        actualP95, lessThanOrEqualTo(maxMillis));
  }

  /**
   * Assert P99 response time is at most a certain value
   */
  @Then("{actor} load test P99 response time should be at most {long} milliseconds")
  public void loadTestP99ResponseTimeShouldBeAtMost(String identifier, long maxMillis) {
    assertLoadTestCompleted();
    long actualP99 = loadTestSummary.getP99ResponseTime().toMillis();
    assertThat(String.format("P99 response time %d ms exceeds maximum %d ms", actualP99, maxMillis),
        actualP99, lessThanOrEqualTo(maxMillis));
  }

  /**
   * Assert max response time is at most a certain value
   */
  @Then("{actor} load test max response time should be at most {long} milliseconds")
  public void loadTestMaxResponseTimeShouldBeAtMost(String identifier, long maxMillis) {
    assertLoadTestCompleted();
    long actualMax = loadTestSummary.getMaxResponseTime().toMillis();
    assertThat(String.format("Max response time %d ms exceeds maximum %d ms", actualMax, maxMillis),
        actualMax, lessThanOrEqualTo(maxMillis));
  }

  /**
   * Assert specific HTTP status code count
   */
  @Then("{actor} load test should have {long} responses with status code {long}")
  public void loadTestStatusCodeCount(String identifier, long expectedCount, int statusCode) {
    assertLoadTestCompleted();
    Long actualCount = loadTestSummary.getStatusCodeDistribution().get(statusCode);
    assertThat(String.format("Expected %d responses with status %d, but got %s",
            expectedCount, statusCode, actualCount),
        actualCount != null ? actualCount : 0L, equalTo(expectedCount));
  }

  /**
   * Assert all responses have specific status code
   */
  @Then("{actor} load test all responses should have status code {long}")
  public void loadTestAllResponsesStatusCode(String identifier, int statusCode) {
    assertLoadTestCompleted();
    Long actualCount = loadTestSummary.getStatusCodeDistribution().get(statusCode);
    int totalRequests = loadTestSummary.getTotalRequests();
    assertThat(String.format("Expected all %d responses to have status %d, but got %s",
            totalRequests, statusCode, actualCount),
        actualCount != null ? actualCount.intValue() : 0, equalTo(totalRequests));
  }

  /**
   * Assert no errors occurred
   */
  @Then("{actor} load test should have no errors")
  public void loadTestShouldHaveNoErrors(String identifier) {
    assertLoadTestCompleted();
    assertThat("Load test had failed requests",
        loadTestSummary.getFailedRequests(), equalTo(0));
    assertThat("Load test had errors",
        loadTestSummary.getErrorDistribution().isEmpty(), equalTo(true));
  }

  /**
   * Log load test summary
   */
  @Then("{actor} print load test summary")
  public void printLoadTestSummary(String identifier) {
    assertLoadTestCompleted();
    log.info(loadTestSummary.toFormattedString());
  }

  // ==================== HELPER METHODS ====================

  private void assertLoadTestInitialized() {
    assertThat("Load test builder not initialized. Use 'prepare load test' step first.",
        loadTestBuilder, is(Matchers.notNullValue()));
  }

  private void assertLoadTestCompleted() {
    assertThat("Load test not executed. Use 'execute load test' step first.",
        loadTestSummary, is(Matchers.notNullValue()));
  }
}