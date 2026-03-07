# Testara API

REST API testing module built on RestAssured with service abstraction, concurrent/load testing, request/response interceptors, and file-based request specifications.

## Dependencies

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-api</artifactId>
</dependency>
```

## Quick Start

```java
// Configure a service in application.properties, then:
Response response = TestApi.rest("my-service")
    .process(Method.GET, "/users/1");

// Access the response
ApiResponseData data = TestApi.response();
Object body = data.getData().getBody();
int status = data.getData().getStatusCode();
```

## Service Configuration

Services are defined in `application.properties` under the `api.service.<name>` prefix:

```properties
# Define a service
api.service.user-api.host=http://localhost:8080
api.service.user-api.port=
api.service.user-api.basePath=/api/v1
api.service.user-api.default_specification=user-api

# Default headers via spec
spec.api.user-api.header.Content-Type=application/json
spec.api.user-api.header.Accept=application/json

# Authentication
api.service.user-api.useBasicAuthentication=true
api.service.user-api.username=admin
api.service.user-api.password=secret

# Proxy
api.service.user-api.proxy.host=proxy.example.com
api.service.user-api.proxy.port=8888
api.service.user-api.proxy.scheme=http
```

### Service Properties (`api.service.<name>.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | String | — | Base URL (e.g. `http://localhost:8080`) |
| `port` | Integer | — | Port number |
| `basePath` | String | `/` | Base path prefix |
| `header` | Map | — | Default headers |
| `parameter` | Map | — | Default query parameters |
| `form_param` | Map | — | Default form parameters |
| `default_specification` | String | — | Name of shared spec to inherit |
| `followRedirects` | boolean | `false` | Follow HTTP redirects |
| `maxRedirect` | int | `0` | Max redirect count |
| `reuseHttpClientInstance` | boolean | `false` | Reuse HTTP client across requests |
| `useBasicAuthentication` | boolean | `false` | Enable basic auth |
| `username` | String | — | Basic auth username |
| `password` | String | — | Basic auth password |
| `usePreemptiveAuthentication` | boolean | `false` | Preemptive authentication |
| `applyDefaultContentIfUndefined` | boolean | `false` | Add default charset to content type |
| `autoCloseIdleConnection` | boolean | `true` | Auto-close idle connections |

### Shared Specification (`spec.api.<name>.*`)

Shared headers, parameters, and form parameters that can be referenced by multiple services:

```properties
spec.api.common.header.charset=UTF-8
spec.api.common.header.Content-Type=application/json
spec.api.common.parameter.page=1
spec.api.common.form_param.field=value
```

### Proxy Configuration (`api.service.<name>.proxy.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | String | — | Proxy host |
| `port` | Integer | — | Proxy port |
| `scheme` | String | `http` | Proxy scheme |
| `withAuthentication` | boolean | `false` | Proxy auth |
| `username` | String | — | Proxy username |
| `password` | String | — | Proxy password |

## Global API Properties (`api.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `api.enable-request-log` | Boolean | `true` | Enable request logging |
| `api.enable-response-log` | Boolean | `true` | Enable response logging |
| `api.request-logging` | Set | `ALL` | Log levels: `ALL`, `BODY`, `PARAMS`, `HEADERS`, `COOKIES`, `METHOD`, `PATH` |
| `api.response-logging` | Set | `BODY` | Log levels: `ALL`, `STATUS`, `BODY`, `HEADERS`, `COOKIES` |
| `api.enable-file-logging` | Boolean | `false` | Log requests/responses to files |
| `api.file-logging-max-body-size` | Integer | `10000` | Max body size for file logs |

### Response Mapping (`response.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `response.report-additional-data` | boolean | `true` | Include extra data in reports |
| `response.default-fields.success` | String | `.success` | JSONPath for success field |
| `response.default-fields.error-code` | String | `.errorCode` | JSONPath for error code |
| `response.default-fields.error-message` | String | `.errorMessage` | JSONPath for error message |

## Building and Executing Requests

### Via `TestApi` Entry Point

```java
// Pre-configured service
RequestBuilder builder = TestApi.rest("my-service");

// Unconfigured (manual setup)
RequestBuilder builder = TestApi.rest();
```

### Programmatic Request

```java
Response response = TestApi.rest("user-api")
    .addHeader("X-Request-Id", "test-123")
    .addQueryParam("status", "active")
    .setBody(Map.of("name", "John", "email", "john@example.com"))
    .setRequestContentType(ContentType.JSON)
    .process(Method.POST, "/users");
```

### From JSON Specification File

Create a file at `src/test/resources/files/create user.json`:

```json
{
  "specification": "user-api",
  "httpMethod": "POST",
  "url": "/users",
  "contentType": "application/json",
  "headers": {
    "X-Request-Id": "test-123"
  },
  "queryParameters": {},
  "pathParameters": {},
  "payload": {
    "name": "John",
    "email": "john@example.com"
  }
}
```

Then execute:

```java
Response response = TestApi.rest().process("create user");
```

### Using `CreateRequestSpecification`

```java
CreateRequestSpecification spec = CreateRequestSpecification.builder()
    .specification("user-api")
    .httpMethod(Method.GET)
    .url("/users/{id}")
    .contentType("application/json")
    .pathParameters(Map.of("id", "123"))
    .build();

Response response = TestApi.rest().process(spec);
```

### Reading the Response

```java
ApiResponseData responseData = TestApi.response();
Object body = responseData.getData().getBody();
int statusCode = responseData.getData().getStatusCode();
Map<String, String> headers = responseData.getData().getHeaders();
```

## Load Testing (ConcurrentRequestBuilder)

### Basic Load Test

```java
LoadTestSummary summary = TestApi.loadTest("user-api")
    .withMethod(Method.GET)
    .withUrl("/health")
    .withConcurrency(10)
    .withTotalRequests(100)
    .executeAndSummarize();
```

### Dynamic Requests

```java
LoadTestSummary summary = TestApi.loadTest("user-api")
    .withMethod(Method.POST)
    .withUrl("/users")
    .withConcurrency(20)
    .withTotalRequests(500)
    .withRampUp(Duration.ofSeconds(5))
    .forEachRequest(index -> DynamicRequest.create()
        .header("X-Request-Id", "req-" + index)
        .bodyJson("name", "User " + index, "email", "user" + index + "@test.com")
        .build())
    .onEachResult(r -> log.info("#{}: {} ({}ms)", r.getRequestIndex(), r.getStatusCode(), r.getDurationMs()))
    .executeAndSummarize();
```

### Rate-Limited Load Test

```java
LoadTestSummary summary = TestApi.loadTest("user-api")
    .withMethod(Method.GET)
    .withUrl("/users")
    .withConcurrency(10)
    .executeWithRate(20.0, Duration.ofSeconds(30)); // 20 req/s for 30s
```

### Lightweight Mode (Memory-Efficient)

```java
LoadTestSummary summary = TestApi.loadTestLightweight()
    .withMethod(Method.GET)
    .withUrl("http://api.example.com/health")
    .withConcurrency(50)
    .withTotalRequests(10000)
    .executeAndSummarize();
```

### Load Test Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `api.load-test-enable-file-logging` | Boolean | `false` | File logging for load tests |
| `api.load-test-enable-console-logging` | Boolean | `false` | Console logging for load tests |
| `api.load-test-log-requests` | Boolean | `true` | Log request details |
| `api.load-test-log-responses` | Boolean | `true` | Log response details |
| `api.load-test-log-body-max-size` | Integer | `10000` | Max body size in logs |
| `api.load-test-log-output-dir` | String | `target/load-test-logs` | Output directory for logs |

## Interceptors

### Built-in Interceptors

| Interceptor | Type | Priority | Description |
|-------------|------|----------|-------------|
| `RequestLoggingInterceptor` | Request | MAX | Console request logging |
| `ResponseLoggingInterceptor` | Response | MAX | Console response logging |
| `StoreResponseInterceptor` | Response | — | Stores response in `ApiResponseData` |
| `FileRequestInterceptor` | Request | MAX-1 | Logs to `target/api-logs/` |
| `FileResponseInterceptor` | Response | MAX-1 | Logs to `target/api-logs/` |

### Custom Interceptor

```java
public class AuthTokenInterceptor implements RequestInterceptor {

  @Override
  public int priority() {
    return 50; // lower = runs first
  }

  @Override
  public InterceptorExecutionMode executionMode() {
    return InterceptorExecutionMode.SYNC;
  }

  @Override
  public void logic(RequestSpecification specification) {
    String token = getAuthToken();
    specification.header("Authorization", "Bearer " + token);
  }
}
```

Register via SPI in `META-INF/services/io.github.ygrip.testara.api.interceptor.RequestInterceptor`:

```
com.myproject.interceptors.AuthTokenInterceptor
```

## Sample `application.properties`

```properties
# Service definition
api.service.cat-api.host=https://catfact.ninja
api.service.cat-api.basePath=/
api.service.cat-api.default_specification=cat-api

# Shared spec
spec.api.cat-api.header.Content-Type=application/json
spec.api.cat-api.header.Accept=application/json

# Logging
api.enable-request-log=true
api.enable-response-log=true
api.request-logging=ALL
api.response-logging=STATUS,BODY

# Response mapping
response.default-fields.success=.success
response.default-fields.error-code=.errorCode
response.default-fields.error-message=.errorMessage
```
