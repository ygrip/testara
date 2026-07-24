# Testara Architecture

> How Testara is put together — the core runtime, the module map, and how each testing
> slice (API, UI, DB, streaming, Elasticsearch) plugs into a single BDD execution model.

For a task-oriented walkthrough see **[GETTING-STARTED.md](GETTING-STARTED.md)**; for conventions
and pitfalls see **[BEST-PRACTICES.md](BEST-PRACTICES.md)**.

---

## 1. What Testara is

Testara is a **modular, full-stack test-automation framework for Java 21**. It gives one
consistent authoring model — Cucumber BDD on top of a custom JUnit 5 engine — for tests that
would otherwise need four or five separate tools:

| You want to test… | Testara slice | Engine underneath |
|---|---|---|
| REST APIs | `testara-api` | RestAssured (on virtual threads) |
| Web / mobile UI | `testara-ui` (+ `-selenium` / `-playwright` / `-appium`) | engine-agnostic adapter |
| SQL / NoSQL data | `testara-database` | JDBC + Mongo driver |
| Kafka streams | `testara-streaming` | Reactor Kafka |
| Search indices | `testara-elastic` | Elasticsearch client |
| SSH / remote exec | `testara-security` | SSHJ |

The value proposition is **uniformity**: the same property resolution, the same
expression/command engine, the same assertion library, the same scenario-scoped dependency
injection, and the same reporting apply across every slice. A feature file can drive an API
call, verify the row it wrote in Postgres, and assert the Kafka event it emitted — with one
vocabulary.

## 2. When you need it (and when you don't)

**Reach for Testara when:**
- You have **cross-layer** end-to-end scenarios (API → DB → stream) and want them in one suite.
- You want **BDD-readable** tests that non-engineers can follow, backed by real Java.
- You need **environment-portable** tests — the same feature runs against dev/staging/prod by
  swapping properties, never by editing steps.
- You want **built-in steps** so most scenarios need little-to-no custom glue code.

**Prefer something lighter when:**
- You need pure unit tests of a single class → plain JUnit is simpler.
- You need micro-benchmarks → use JMH.
- The project is single-slice and tiny → a bare RestAssured or Playwright test may be enough.

## 3. The module map

```
testara-core          Foundation: config binding, class scanning, SPI factories,
                      TestComponent DI, DataHolder, ObjectConverter, scopes
  │
  ├── testara-command      Command/expression engine — commands() & properties()
  ├── testara-validation    AssertJ-based assertion & declarative validation rules
  ├── testara-properties    External config from Consul / Vault
  │
  ├── testara-api           REST client (RestAssured), request specs, load testing
  ├── testara-ui            Engine-agnostic UI core (Page, Actor, capabilities)
  │     ├── testara-ui-selenium
  │     ├── testara-ui-playwright
  │     └── testara-ui-appium
  ├── testara-database      Mongo / Postgres / MariaDB / MySQL
  ├── testara-streaming     Kafka producer/consumer
  ├── testara-elastic       Elasticsearch
  └── testara-security      SSH / remote command execution
  │
  ├── testara-cucumber      BDD wiring: ObjectFactory, lifecycle hooks, common steps
  │     ├── testara-api-cucumber
  │     ├── testara-ui-cucumber
  │     ├── testara-database-cucumber
  │     ├── testara-streaming-cucumber
  │     └── testara-elastic-cucumber
  ├── testara-junit5        Custom JUnit 5 TestEngine (parallel execution)
  ├── testara-spring        Optional Spring Boot auto-configuration
  ├── testara-reporter[-plugin]   HTML reports from Cucumber JSON
  ├── testara-bom           Version alignment (import in dependencyManagement)
  └── testara-agent[-cli/-mcp]    AI-assisted scaffolding, planning, review, run
```

**Rule of thumb:** a `-cucumber` module is *steps only* — it depends on the matching capability
module (e.g. `testara-api-cucumber` → `testara-api`). Add the `-cucumber` artifact and you get
the built-in Gherkin vocabulary for that slice for free.

## 4. Core runtime concepts

### 4.1 Components and scopes (`testara-core`)

Testara has its own lightweight DI. Any class annotated `@TestComponent` is discovered by
classpath scanning and instantiated by the framework, with a **scope**:

- `RegistryScope.GLOBAL` — one instance for the JVM (config holders, shared caches).
- `RegistryScope.TEST` — **one instance per scenario**, keyed by a per-scenario scope key held
  in a `ThreadLocal`. This is what makes parallel scenarios isolated: two threads running two
  scenarios get two independent step instances and two independent `DataHolder`s.

`@LoadProperties(prefix = "...")` on a component binds external configuration onto its fields
(e.g. `ApiProperties` binds everything under `api.*`).

### 4.2 The command / expression engine (`testara-command`)

`CommandExecutor` scans for `CommandLogic<T>` implementations (`@CommandTag`) and evaluates
expressions embedded in test data:

```
combine(uuid(), delimiter(-), fakename())   → "a1b2… - John Doe"
timetravel(2, day, yyyy-MM-dd)               → date two days out
properties(user-api.baseUrl)                 → value from config
```

Two commands are load-bearing everywhere:
- **`properties(key[, default])`** — reads a configuration property. This is how tests stay
  environment-portable: never hardcode a URL/credential/topic — wrap it in `properties(...)`.
- **`commands(...)`** / nested commands — generate or transform data at run time.

`CommandExecutor` is a static singleton initialised at class load; it caches parse results and
(opt-in `cacheable`) execution results. See `testara-command/README.md` for the full 50+ command
catalogue.

### 4.3 Shared scenario state — `DataHolder`

Steps communicate through a scenario-scoped `DataHolder` (`request` / `response` maps). An API
call stores its response there; a later validation step reads it via `TestApi.response()` or the
`response(path)` / `request(path)` commands. Because `DataHolder` is `TEST`-scoped, state never
bleeds between scenarios.

### 4.4 BDD wiring (`testara-cucumber`)

- **`TestaraObjectFactory`** — Cucumber's `ObjectFactory`. Initialises the framework once, scans
  for components, and resolves each glue class through a fallback chain
  (framework factory → Spring SPI → registry → `new`).
- **Lifecycle hooks** — `TestaraLifecycleHooks` enters/clears the scenario scope and sets logging
  MDC; `DataCleanUpHooks` resets request/response data between scenarios.
- **`BaseDefinitions`** — Cucumber `@ParameterType`s (the `{actor}`, `{httpMethod}`,
  `{shouldOrShouldNot}`, … tokens) shared by all step libraries.

### 4.5 The generation / execution chain

Everything above composes into one dependency chain that Testara (and the agent) always
respects, left to right:

```
properties → commands → config → base-steps → request-specs → pages/actions → screenplay → compile
```

Read it as: environment values come from **properties**, transformed by **commands**, bound into
per-slice **config**, consumed by **base-steps**, which reference **request specs** (API) or
**pages/actions** (UI), executed through the **screenplay** actor model, and finally **compiled**
and run.

## 5. API slice architecture (`testara-api`)

```
ApiBaseSteps (cucumber)
  └─ TestApi.rest(service)  ──► RequestBuilderImpl  (RegistryScope.TEST)
        builds CreateRequestSpecification from a .json spec + per-call setters
        merges ApiModel (service config) over per-call values, resolves properties()/commands()
        ──► VirtualRestAssured.asyncCall(...)   (RestAssured on a shared virtual-thread executor)
        ──► request interceptors → send → response interceptors
        ──► StoreResponseInterceptor snapshots into ApiResponseData (DataHolder)
  Then-steps read TestApi.response()
```

- **Config**: `ApiProperties` (prefix `api`) holds `Map<String, ApiModel> service`. Each
  `ApiModel` carries host/port/basePath/headers/params/proxy and a reference to a reusable
  `DefaultApiSpec`. `SharedServiceConfigCache` (GLOBAL) merges default-spec + service config into
  an immutable `ServiceConfig`.
- **Request spec JSON** (`CreateRequestSpecification`): `specification` (service name),
  `httpMethod`, `url`, `headers`, `queryParameters`, `pathParameters`, `formParameters`,
  `payload`, `multiPartData`, `cookies`, `requestLog`/`responseLog`.
- **Load testing**: `TestApi.loadTest()` → `ConcurrentRequestBuilderImpl` (virtual-thread pool +
  semaphore) with a rich percentile/throughput/status assertion surface.

## 6. UI slice architecture (`testara-ui`)

Engine-agnostic by design. `testara-ui` holds all abstractions; the `-selenium`/`-playwright`/
`-appium` modules are adapters selected at runtime via `engine.default-engine` /
`engine.active-engines`.

```
UIBaseSteps (cucumber)
  └─ ActorManager.currentActor()
       └─ Actor.attemptsTo(Interaction...) / observe(Observation) / executeTask("<task>")
            └─ InteractionContext → DriverSession.capability(X)
                 └─ engine Capability impl (Navigation/Interaction/Assertion/Wait/Observation)
                      └─ real Playwright Page / Selenium WebDriver / Appium driver
```

- **`@Page` model**: a page object extends the engine base (`PlaywrightPage`/`SeleniumPage`/
  `AppiumPage` → `PageContext`). The page **URL lives in properties**
  (`web.page.<device>.<name>.url`, bound by `WebPageDataProperties`), *not* hardcoded in the
  annotation — `@Page(url=...)` defaults to `""` and should stay empty.
- **Screenplay**: `Actor.attemptsTo(Interaction...)` (Click, Enter, Navigate, WaitUntil, SeeThat…),
  `Actor.observe(Observation)` for reads. `UserAction` subclasses expose `@Action`-annotated,
  `@OnPage`-scoped tasks invoked by `ActionResolver` for the Gherkin `do "<task>"` steps.
- **Engine seam**: `DriverSession.capability(Class)` returns the engine's implementation of one
  of five capability interfaces. `EngineFactory` scans `@DriverMetadata` drivers.
- **Concurrency note**: Playwright-Java is not thread-safe, so `PlaywrightSession` serialises all
  API calls onto one dedicated worker thread and re-binds the session/actor thread-locals there.

## 7. Data, streaming, and search slices

- **Database** (`testara-database-cucumber`): `SqlBaseSteps` / `MongoBaseSteps`, addressed with
  literal bracket tokens `[sql]` / `[mongo]`. Connect by named datasource, prepare a query, execute,
  then `assign previous database response to <name>` for later assertions.
- **Streaming** (`testara-streaming-cucumber`): `KafkaConsumerSteps` / `KafkaPublisherSteps` —
  start a consumer, publish, `assign N latest records from topic "…"`, assert, stop.
- **Elasticsearch** (`testara-elastic-cucumber`): `ElasticSearchBaseSteps`, addressed `[elastic-search]`.

All three share the same `DataHolder`, `properties()`/`commands()`, and validation vocabulary as
the API and UI slices.

## 8. The agent layer (`testara-agent`)

An AI-assist layer that reads a Testara project and helps author/run tests correctly. It is not
required at runtime — it is a CLI + MCP server used during development.

- **`ProjectIndexer`** scans source for commands, validations, step definitions, drivers, the
  flavor (built-in step) catalog, and the runtime config catalog, producing a
  `TestaraProjectProfile`.
- **`JsonlKnowledgeStore`** caches that profile under `.testara-agent/knowledge/` with
  fingerprint-based incremental reindexing (cold ~3s → warm ~0.5s).
- **Skills / MCP tools** (`testara_guide`, `testara_plan`, `testara_run`, `testara_api`,
  `testara_ui`, …) generate flavor-aware features, request specs, pages, and configuration,
  gated by `GenerationGuard` (flags hardcoded URLs/credentials, missing scan-locations, etc.).

See **[agentic-skills.md](agentic-skills.md)** for the full skill/MCP reference.

## 9. Reporting

`testara-reporter` / `testara-reporter-plugin` turn Cucumber JSON into HTML reports (Thymeleaf
templates). The JUnit 5 engine (`testara-junit5`) supports parallel scenario execution; the
per-scenario `TEST` scope is what keeps that parallelism safe.
