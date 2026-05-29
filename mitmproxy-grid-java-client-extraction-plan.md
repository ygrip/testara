# MitmProxy Grid Java Client Extraction Plan

## Target

Extract the current MitmProxy Grid Java client implementation from `ygrip/testara` into a dedicated repository:

```text
git@github.com:ygrip/mitmproxy-grid-java-client.git
```

The new repository should publish a standalone Java SDK that can be consumed by Testara and by other Java-based automation projects without pulling Testara UI/Core dependencies.

Recommended Maven coordinates:

```xml
<groupId>io.github.ygrip</groupId>
<artifactId>mitmproxy-grid-java-client</artifactId>
<version>0.1.0</version>
```

Recommended base package:

```text
io.github.ygrip.mitmproxy.grid.client
```

---

## Current Implementation Summary

The existing implementation is inside Testara, mostly under `testara-ui` and the UI driver adapter modules.

### Main SDK-like class

| Current file | Current role | Extraction action |
|---|---|---|
| `testara-ui/src/main/java/io/github/ygrip/testara/ui/proxy/MitmProxyClient.java` | HTTP client for MitmProxy Grid REST API v2. Handles health, instance CRUD, TTL renewal, rule CRUD, CA cert retrieval, retry, and URL normalization. | Move into the new SDK as the primary client class. Rename package only. |

### DTO / model classes to move

Move these classes from `testara-ui/src/main/java/io/github/ygrip/testara/ui/model/` into the new SDK:

| Model | Reason |
|---|---|
| `MitmProxyRule` | Public rule creation model and convenience factories. |
| `MitmProxyRuleMatch` | Rule match condition model. |
| `MitmProxyRuleAction` | Rule action model. |
| `MitmProxyRequestModification` | Request mutation model. |
| `MitmProxyResponseModification` | Response mutation model. |
| `MitmProxyHeaderModification` | Header mutation model. |
| `MitmProxyParamModification` | Query parameter mutation model. |
| `MitmProxyBodyReplace` | Body replace model. |
| `MitmProxyRuleResponse` | Rule list response model. |
| `MitmProxyHealthResponse` | Grid health response model. |
| `MitmProxyCreateInstanceResponse` | Instance creation response model. |
| `MitmProxyInstanceSummary` | Instance list response model. |
| `MitmProxyInstanceDetail` | Instance detail response model. |
| `MitmProxyRenewResponse` | TTL renewal response model. |
| `MitmProxyMessageResponse` | Generic API message response model. |

### Optional convenience model

| Current file | Recommendation |
|---|---|
| `ProxyRuleCreation.java` | Do not move as-is because it depends on Testara `FileHelper`. Reimplement in the SDK as `MitmProxyRuleSpec` or `MitmProxyRuleFileSpec` using only JDK `Files.readString`, `Files.readAllBytes`, and `Path`. Keep Testara-specific transformer/resource resolution in Testara. |

### Testara classes that should remain in Testara

| Current file | Why it stays |
|---|---|
| `AbstractProxy.java` | Testara proxy abstraction, uses Testara `DataHolder` and driver lifecycle concepts. |
| `ProxyInstanceManager.java` | Testara lifecycle manager using `ResourceShutdownRegistry`. |
| `ProxyProperties.java` | Testara property binding with `@LoadProperties`. |
| `MitmProxySeleniumUtility.java` | Selenium adapter; should depend on the new SDK. |
| `MitmProxyPlaywrightUtility.java` | Playwright adapter; should depend on the new SDK. |
| `MitmProxyAppiumUtility.java` | Appium adapter; should depend on the new SDK. |
| `ProxySteps.java` | Cucumber-facing Testara DSL; should remain in Testara. |

---

## Design Goals

1. **Zero Testara dependency**
   - The SDK must not depend on `testara-core`, `testara-ui`, `MapperHelper`, `FileHelper`, `DataHolder`, `TestComponent`, or Testara registry annotations.

2. **Small dependency surface**
   - Prefer JDK `java.net.http.HttpClient` for transport.
   - Use Jackson directly for JSON serialization/deserialization.
   - Lombok can be used initially for fast extraction, but for a public SDK the preferred final form is Java records or plain POJOs to avoid leaking annotation processing requirements to contributors.

3. **Stable API boundary**
   - Keep the current client operations intact.
   - Add typed exception handling and configurable timeouts/retries.
   - Preserve current convenience factories such as `mockResponse`, `replaceResponseBody`, `replaceRequestBody`, `setRequestHeaders`, `setResponseHeaders`, `block`, `setQueryParams`, `disableCaching`, `replaceImage`, and `replaceImageBase64`.

4. **Framework agnostic**
   - No Selenium, Playwright, Appium, Cucumber, Spring, or Testara types in the SDK core.
   - Browser-specific proxy conversion remains in Testara adapters.

5. **Backward-compatible Testara migration**
   - Existing Testara public APIs and Cucumber steps should continue working after package imports are replaced.

---

## Proposed New Repository Structure

```text
mitmproxy-grid-java-client/
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
├── src/
│   ├── main/
│   │   └── java/
│   │       └── io/github/ygrip/mitmproxy/grid/client/
│   │           ├── MitmProxyGridClient.java
│   │           ├── MitmProxyGridClientBuilder.java
│   │           ├── MitmProxyGridClientConfig.java
│   │           ├── exception/
│   │           │   ├── MitmProxyGridException.java
│   │           │   ├── MitmProxyGridHttpException.java
│   │           │   └── MitmProxyGridTimeoutException.java
│   │           ├── model/
│   │           │   ├── MitmProxyBodyReplace.java
│   │           │   ├── MitmProxyCreateInstanceResponse.java
│   │           │   ├── MitmProxyHeaderModification.java
│   │           │   ├── MitmProxyHealthResponse.java
│   │           │   ├── MitmProxyInstanceDetail.java
│   │           │   ├── MitmProxyInstanceSummary.java
│   │           │   ├── MitmProxyMessageResponse.java
│   │           │   ├── MitmProxyParamModification.java
│   │           │   ├── MitmProxyRenewResponse.java
│   │           │   ├── MitmProxyRequestModification.java
│   │           │   ├── MitmProxyResponseModification.java
│   │           │   ├── MitmProxyRule.java
│   │           │   ├── MitmProxyRuleAction.java
│   │           │   ├── MitmProxyRuleMatch.java
│   │           │   └── MitmProxyRuleResponse.java
│   │           └── support/
│   │               ├── BinaryFileDetector.java
│   │               └── JsonCodec.java
│   └── test/
│       └── java/
│           └── io/github/ygrip/mitmproxy/grid/client/
│               ├── MitmProxyGridClientTest.java
│               ├── MitmProxyRuleTest.java
│               └── MitmProxyRuleFileSpecTest.java
├── pom.xml
├── README.md
├── CHANGELOG.md
├── LICENSE
└── .gitignore
```

Recommended class rename:

```text
Current:  io.github.ygrip.testara.ui.proxy.MitmProxyClient
New:      io.github.ygrip.mitmproxy.grid.client.MitmProxyGridClient
```

A compatibility alias can be added for one release cycle:

```java
@Deprecated(forRemoval = true, since = "0.1.0")
public class MitmProxyClient extends MitmProxyGridClient {
  public MitmProxyClient(String apiBaseUrl) {
    super(apiBaseUrl);
  }
}
```

Use the alias only inside the SDK if you want easier migration. Avoid keeping two public names long term.

---

## SDK API Shape

### Minimal usage

```java
MitmProxyGridClient client = MitmProxyGridClient.builder()
    .baseUrl("http://localhost:8090")
    .connectTimeout(Duration.ofSeconds(10))
    .requestTimeout(Duration.ofSeconds(10))
    .maxRetries(3)
    .retryBaseDelay(Duration.ofMillis(500))
    .build();

client.waitUntilReady(Duration.ofSeconds(30), Duration.ofSeconds(1));
MitmProxyCreateInstanceResponse instance = client.createInstance(300);

client.createRule(instance.getInstanceId(), MitmProxyRule.mockResponse(
    "api.example.com/users",
    200,
    Map.of("mocked", true)
));

client.clearAllRules(instance.getInstanceId());
client.destroyInstance(instance.getInstanceId(), true);
```

### Required operations

| Area | Methods |
|---|---|
| Health | `health()`, `healthRaw()`, `isReady()`, `waitUntilReady(...)` |
| Instances | `createInstance(Integer ttl)`, `createInstance()`, `listInstances()`, `getInstance(String id)`, `destroyInstance(String id, boolean cleanup)`, `destroyInstance(String id)`, `renewInstance(String id, Integer ttl)`, `renewInstance(String id)` |
| Bulk cleanup | `destroyAllInstances(boolean cleanup)`, `destroyAllInstances()` |
| Rules | `createRule(String instanceId, MitmProxyRule rule)`, `listRules(String instanceId)`, `deleteRule(String instanceId, int ruleIndex)`, `toggleRule(String instanceId, int ruleIndex)`, `clearAllRules(String instanceId)` |
| Certificate | `getCaCertificate(String instanceId)` |

### Configuration object

```java
public record MitmProxyGridClientConfig(
    URI baseUri,
    Duration connectTimeout,
    Duration requestTimeout,
    int maxRetries,
    Duration retryBaseDelay,
    ObjectMapper objectMapper
) {}
```

Validation rules:

- `baseUri` defaults to `http://localhost:8090/`.
- Normalize missing trailing slash.
- `maxRetries` must be `>= 1`.
- `connectTimeout`, `requestTimeout`, and `retryBaseDelay` must be positive.
- `objectMapper` defaults to a locally configured Jackson mapper.

### Exception model

Replace broad `throws Exception` and `RuntimeException` with typed SDK exceptions:

```text
MitmProxyGridException
├── MitmProxyGridHttpException
├── MitmProxyGridTimeoutException
└── MitmProxyGridSerializationException
```

`MitmProxyGridHttpException` should expose:

```java
int statusCode();
String method();
String path();
String responseBody();
```

---

## Dependency Plan

### New SDK dependencies

Minimum viable Maven dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>

  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>

  <dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8-standalone</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Alternative for Java 21 projects: use a current WireMock artifact compatible with Java 21 if the project already standardizes on WireMock 3.x.

### Avoid in the SDK

Do not include these in the extracted SDK:

- `testara-core`
- `testara-ui`
- Selenium
- Playwright
- Appium
- BrowserUp Proxy
- Spring / Spring Boot
- Cucumber
- Log4j as a required API dependency

For logging, prefer either:

1. no logging in the SDK, or
2. `slf4j-api` only.

---

## Testara Migration Plan

### Step 1 — Add dependency to Testara parent/dependency management

Add the SDK version property in `pom.xml`:

```xml
<mitmproxy-grid-java-client.version>0.1.0</mitmproxy-grid-java-client.version>
```

Add dependency management:

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>mitmproxy-grid-java-client</artifactId>
  <version>${mitmproxy-grid-java-client.version}</version>
</dependency>
```

### Step 2 — Add dependency to `testara-ui`

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>mitmproxy-grid-java-client</artifactId>
</dependency>
```

### Step 3 — Replace imports in Testara

Current imports:

```java
import io.github.ygrip.testara.ui.model.MitmProxyRule;
import io.github.ygrip.testara.ui.proxy.MitmProxyClient;
```

New imports:

```java
import io.github.ygrip.mitmproxy.grid.client.MitmProxyGridClient;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRule;
```

Apply this to:

- `AbstractProxy.java`
- `MitmProxySeleniumUtility.java`
- `MitmProxyPlaywrightUtility.java`
- `MitmProxyAppiumUtility.java`
- `ProxySteps.java`
- any other files importing `MitmProxy*` DTOs from `io.github.ygrip.testara.ui.model`

### Step 4 — Remove duplicated SDK models from Testara

Delete the moved DTOs from `testara-ui/src/main/java/io/github/ygrip/testara/ui/model/` only after all imports compile.

Do not delete Testara-specific models such as:

- `AvailableProxy`
- `TestaraProxyModel`
- other UI framework models not related to MitmProxy Grid API payloads

### Step 5 — Keep Testara lifecycle adapters

The Testara adapters continue to own framework-specific logic:

| Adapter | Still responsible for |
|---|---|
| Selenium | Convert created instance port into Selenium `Proxy`. |
| Playwright | Convert created instance port into Playwright proxy URL string. |
| Appium | Convert created instance port into Appium/Selenium `Proxy`. |
| AbstractProxy | Expose Testara DSL-level proxy lifecycle and rule APIs. |
| ProxyInstanceManager | Thread-local scenario/run lifecycle cleanup. |

### Step 6 — Improve duplicated adapter logic later

After extraction, optionally introduce a small Testara-local helper:

```text
MitmProxyGridSessionSupport
```

Responsibilities:

- lazy-create `MitmProxyGridClient`
- wait until ready
- create instance
- clear rules after scenario
- renew TTL after scenario
- destroy instance with cleanup
- expose `instanceId`, `port`, `started`

This removes duplicated lifecycle code across Selenium, Playwright, and Appium without polluting the standalone SDK with Testara concepts.

---

## Implementation Phases

## Phase 0 — Repository Bootstrap

Create the new repository if it does not exist locally:

```bash
git clone git@github.com:ygrip/mitmproxy-grid-java-client.git
cd mitmproxy-grid-java-client
```

If the repository is empty, initialize:

```bash
cat > pom.xml
mkdir -p src/main/java src/test/java
mkdir -p .github/workflows
```

Initial repository files:

- `pom.xml`
- `README.md`
- `LICENSE`
- `CHANGELOG.md`
- `.gitignore`
- `.github/workflows/ci.yml`

Acceptance criteria:

- `mvn -q test` runs successfully with an empty skeleton.
- GitHub Actions CI runs on `push` and `pull_request`.

---

## Phase 1 — Move Client and DTOs

Copy and repackage:

```text
MitmProxyClient.java -> MitmProxyGridClient.java
io.github.ygrip.testara.ui.model.* -> io.github.ygrip.mitmproxy.grid.client.model.*
```

Replace `MapperHelper` usage:

Current behavior:

```java
MapperHelper.toObject(response, MitmProxyHealthResponse.class)
MapperHelper.toObject(response, new TypeReference<List<MitmProxyInstanceSummary>>() {})
MapperHelper.toString(rule)
```

New SDK behavior:

```java
objectMapper.readValue(response, MitmProxyHealthResponse.class)
objectMapper.readValue(response, new TypeReference<List<MitmProxyInstanceSummary>>() {})
objectMapper.writeValueAsString(rule)
```

Acceptance criteria:

- SDK has no imports from `io.github.ygrip.testara`.
- SDK compiles independently.
- Current API methods are available.

---

## Phase 2 — Hardening and API Cleanup

Refactor the raw client into a cleaner SDK surface:

- Add `MitmProxyGridClientConfig`.
- Add builder class or static `builder()` method.
- Replace `throws Exception` with typed runtime or checked SDK exceptions.
- Make retry count and delay configurable.
- Keep the current constructor for convenience:

```java
public MitmProxyGridClient(String apiBaseUrl)
```

- Add constructor for testability:

```java
public MitmProxyGridClient(MitmProxyGridClientConfig config, HttpClient httpClient)
```

Acceptance criteria:

- Existing simple usage remains concise.
- Tests can inject fake/local HTTP server configuration.
- Error responses expose status code, path, method, and body.

---

## Phase 3 — Unit and Contract Tests

Use WireMock or JDK HTTP test server to cover:

| Test area | Cases |
|---|---|
| URL normalization | null, empty, missing trailing slash, existing trailing slash |
| Health | `health()`, `healthRaw()`, `isReady()` true/false |
| Wait readiness | ready immediately, ready after retries, timeout |
| Instance lifecycle | create/list/get/renew/destroy |
| Rule lifecycle | create/list/delete/toggle/clear all in reverse index order |
| Certificate | PEM response handling |
| Retry | transient 500 then success, max retry exhausted |
| Error mapping | 4xx/5xx response converted to typed exception |
| Serialization | rule JSON shape matches grid OpenAPI expectation |
| Binary body | image/file base64 helper works correctly |

Acceptance criteria:

- At least 80% line coverage for SDK core.
- Tests do not require a real MitmProxy Grid server.
- Optional integration test profile can run against real `http://localhost:8090`.

Suggested Maven profiles:

```xml
<profile>
  <id>integration</id>
  <activation>
    <property>
      <name>it</name>
    </property>
  </activation>
</profile>
```

Run:

```bash
mvn verify -Dit -Dmitmproxy.grid.url=http://localhost:8090
```

---

## Phase 4 — Documentation

Create `README.md` with:

1. installation
2. quick start
3. client configuration
4. instance lifecycle example
5. rule creation examples
6. Selenium/Testara integration example
7. error handling
8. release/versioning policy

Example install section:

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>mitmproxy-grid-java-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

Example rule section:

```java
client.createRule(instanceId, MitmProxyRule.disableCaching());
client.createRule(instanceId, MitmProxyRule.block("/analytics"));
client.createRule(instanceId, MitmProxyRule.replaceResponseBody(
    "/feature-flag",
    "false",
    "true"
));
```

Acceptance criteria:

- README is enough for non-Testara users to consume the SDK.
- README clearly states Java baseline.
- README states that browser driver integration is intentionally outside the SDK.

---

## Phase 5 — CI and Release

### CI workflow

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: mvn -B verify
```

### Release workflow options

Start simple:

- deploy GitHub Packages first, or
- publish to Maven Central if this should be public and stable.

Recommended phased publishing:

1. `0.1.0-SNAPSHOT` through local Maven or GitHub Packages.
2. Consume from Testara branch.
3. Release `0.1.0` after Testara integration passes.
4. Later publish to Maven Central.

Acceptance criteria:

- CI passes on PR.
- A release tag can produce an artifact.
- Testara can consume the artifact from local Maven or package registry.

---

## Phase 6 — Testara Integration Branch

Create a Testara branch:

```bash
cd testara
git checkout -b refactor/extract-mitmproxy-grid-java-client
```

Local install the SDK first:

```bash
cd ../mitmproxy-grid-java-client
mvn clean install
```

Update Testara:

```bash
cd ../testara
mvn -q -pl testara-ui -am test
```

Then run broader checks:

```bash
mvn -q test
mvn -q -pl testara-ui-selenium,testara-ui-playwright,testara-ui-appium -am test
```

Acceptance criteria:

- `testara-ui` no longer contains SDK DTOs or raw HTTP client.
- Selenium/Playwright/Appium modules compile with the new SDK dependency.
- Existing Testara proxy behavior remains source-compatible for users.

---

## Phase 7 — Compatibility and Deprecation Strategy

Because existing Testara users may import classes like:

```java
io.github.ygrip.testara.ui.model.MitmProxyRule
```

Choose one of these strategies:

### Option A — Breaking cleanup

Remove old classes from Testara immediately.

Pros:

- clean architecture
- no duplicate DTOs
- no long-term maintenance burden

Cons:

- user code importing Testara DTOs breaks

Recommended only if this is before a stable public Testara release.

### Option B — Compatibility wrappers for one minor release

Keep deprecated forwarding classes in Testara:

```java
@Deprecated(forRemoval = true, since = "1.2.0")
public class MitmProxyRule extends io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRule {
}
```

This is difficult if the DTOs use Lombok builders heavily, because static builder methods do not inherit cleanly.

Pros:

- gentler migration

Cons:

- annoying builder compatibility
- duplicate source burden
- may create type mismatch issues

### Recommendation

Use **Option A** for DTOs and document the import change clearly. Keep Testara DSL methods source-compatible where possible, but let direct DTO imports move to the new package.

---

## Refactoring Details

### Replace `MapperHelper`

Create `JsonCodec`:

```java
final class JsonCodec {
  private final ObjectMapper objectMapper;

  JsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper.copy()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new MitmProxyGridSerializationException("Failed to deserialize response", e);
    }
  }

  <T> T read(String json, TypeReference<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new MitmProxyGridSerializationException("Failed to deserialize response", e);
    }
  }

  String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new MitmProxyGridSerializationException("Failed to serialize request", e);
    }
  }
}
```

### Replace `FileHelper`

In the SDK, replace Testara file reading with JDK APIs:

```java
String text = Files.readString(path, StandardCharsets.UTF_8);
byte[] bytes = Files.readAllBytes(path);
String base64 = Base64.getEncoder().encodeToString(bytes);
```

### Preserve binary extension detection

Move binary detection into:

```text
support/BinaryFileDetector.java
```

Current recognized extensions should be preserved:

```text
png, jpg, jpeg, gif, webp, ico, bmp, tiff, avif,
woff, woff2, ttf, eot, pdf, zip, gz, br
```

### Make request creation safer

Current path concatenation is simple and practical. Improve with internal path normalization:

```java
private URI resolve(String path) {
  String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
  return baseUri.resolve(normalizedPath);
}
```

Keep query strings supported.

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---:|---|
| DTO package changes break user imports | Medium | Document migration clearly and release Testara with changelog entry. |
| Lombok builder compatibility breaks if wrappers are attempted | Medium | Prefer direct package migration instead of wrappers. |
| SDK accidentally depends on Testara internals | High | Add Maven Enforcer banned dependency/import check or CI grep. |
| JSON shape changes during refactor | High | Add serialization contract tests for representative rules. |
| Retry behavior changes | Medium | Test retry sequence and make retry config explicit. |
| Testara adapters keep duplicated lifecycle code | Low | Extract Testara-local `MitmProxyGridSessionSupport` after SDK migration. |
| Publishing friction delays Testara integration | Medium | Start with `mvn install` and GitHub Packages before Maven Central. |

---

## Migration Checklist

### New SDK repository

- [ ] Initialize repository structure.
- [ ] Add Maven `pom.xml` with Java 21 baseline.
- [ ] Add Apache 2.0 license if aligned with Testara.
- [ ] Move/repackage `MitmProxyClient` as `MitmProxyGridClient`.
- [ ] Move/repackage MitmProxy DTOs.
- [ ] Replace `MapperHelper` with Jackson-based `JsonCodec`.
- [ ] Reimplement file helpers using JDK APIs.
- [ ] Add typed SDK exceptions.
- [ ] Add builder/configuration support.
- [ ] Add unit tests with mocked HTTP server.
- [ ] Add README quick start and examples.
- [ ] Add CI workflow.
- [ ] Publish `0.1.0-SNAPSHOT` or install locally.

### Testara repository

- [ ] Add SDK dependency to parent dependency management.
- [ ] Add SDK dependency to `testara-ui`.
- [ ] Replace imports from `io.github.ygrip.testara.ui.model.MitmProxy*` to SDK model package.
- [ ] Replace `MitmProxyClient` imports with `MitmProxyGridClient`.
- [ ] Delete moved DTO classes from `testara-ui`.
- [ ] Keep Testara-specific proxy abstractions and driver adapters.
- [ ] Run targeted module tests.
- [ ] Run full Testara build.
- [ ] Update Testara README/changelog with migration note.

---

## Suggested PR Breakdown

### PR 1 — Bootstrap SDK repository

Scope:

- Maven skeleton
- package structure
- CI
- README stub

No Testara changes.

### PR 2 — Move SDK client and models

Scope:

- `MitmProxyGridClient`
- model classes
- Jackson codec
- exception model
- unit tests

No Testara changes yet.

### PR 3 — Integrate SDK into Testara

Scope:

- add dependency
- replace imports
- delete moved DTOs/client from Testara
- compile Selenium/Playwright/Appium modules

### PR 4 — Deduplicate Testara adapter lifecycle

Scope:

- optional `MitmProxyGridSessionSupport`
- remove repeated lifecycle logic from Selenium/Playwright/Appium adapters

This should be done after behavior is proven unchanged.

---

## Final Recommended Boundary

The dedicated `mitmproxy-grid-java-client` repository should own:

- HTTP transport to MitmProxy Grid
- request/response DTOs
- rule builder/convenience factories
- retry/timeout/error handling
- CA cert retrieval
- optional file-to-rule helper that uses only JDK APIs

Testara should own:

- Selenium/Playwright/Appium proxy conversion
- Testara scenario/run lifecycle
- Testara Cucumber steps
- Testara property loading
- Testara resource resolution and transformer integration

This gives you a clean, reusable SDK while keeping Testara as the automation framework integration layer.
