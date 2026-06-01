# Testara Agent Generation Rules

## Testara runtime chain
properties -> command conversion -> config binding -> base steps -> request specs -> pages/actions -> screenplay -> compile gate

## Agent quick guardrails
- Read this section first; open detailed sections only when generating that artifact type.
- **UI projects:** read "UI Runtime Quirks" section before generating any feature or page artifact.
- Prefer built-in Testara steps/helpers over custom Cucumber steps.
- Keep reusable Java in `src/main/java/{basePackage}`; keep runners/glue in `src/test/java/{basePackage}`.
- `test-init` is a clean bootstrap by default: do not generate placeholder pages, actions, StepDefinitions, sample features, sample request specs, or sample service aliases unless `includeExamples=true` is explicitly requested.
- `test-init` must ask for `groupId` and `artifactId` unless the user explicitly chooses auto-generated coordinates.
- `test-init` writes only to the intended workspace. When using MCP, pass `projectRoot` explicitly if the server root is not the target project — this applies to `testara_init`, `testara_run`, and `testara_context`.
- Compile scope for Testara modules imported by `src/main/java`; test scope for `testara-*-cucumber`, `testara-junit5`, and JUnit runner deps.
- Do not add `cucumber-junit-platform-engine` to generated POMs. Testara provides `testara-cucumber` engine; adding the standard engine causes conflicts.
- Do not pin `junit-platform-suite`; let Testara/JUnit transitive versions follow the resolved `testara.version`.
- Property files: framework wiring belongs in `configuration.properties`; user/env values belong in `application.properties` or `application-{env}.properties`. Do not set property values to `properties(other.key)`.
- Config values use `${ENV:fallback}`. Feature/request values may use `properties(key)` for application values; dynamic values should use commands such as `uuid()`, `random(...)`, or `oneOf(...)`.
- API: use request specs at `src/test/resources/files/{domain}/request/{flow}.json`; feature path is `files/{domain}/request/{flow}`.
- UI: generate `page` + `action`; use reusable action labels such as `login with credentials`; infer/ask pageName instead of producing SamplePage/SampleActions.
- UI: keep scenario/video recording disabled by default. Generate `automation.engine.screenshot-output-type=IMAGE`; use `VIDEO` only when the user explicitly asks for scenario recording.
- UI plans must emit executable Testara steps. `# MISSING` in generated UI login flows means the plan is not done.
- UI: `testara_ui` emits `TODO-replace-selector` placeholders for unknown page types — replace all of them with real DOM selectors before generating features that reference those elements.
- UI plans: use page names and action names that exactly match existing Page/UserAction classes. Do not invent names for pages or actions not yet created.
- UI UserAction classes must be top-level classes under `src/main/java/{basePackage}/action`, annotated with `@OnPage`, and must extend `UserAction` directly. Do not generate wrapper classes with nested `static class ... extends UserAction`; the action scanner expects discoverable top-level action classes.
- UI cross-page/global actions: if an action is intentionally callable from multiple pages, keep it in a top-level `UserAction` class and annotate the method with `@Action(value = "action name", allowAnonymousCall = true)`. Do not use nested classes to model cross-page access.
- DB/Kafka/Elastic: use built-in steps and exact property prefixes (`sql.service`, `mongo.service`, `kafka.service`, `elasticsearch.service`).
- DataTables: use `| key | value |` for map-shaped queries/params; use horizontal rows for records/templates/validations.
- Scan locations must include `io.github.ygrip.testara` and singular project packages: `.command`, `.validation`, `.page`, `.action`.
- Compile generated scaffolds with `mvn test-compile`; run full tests only when Docker/Testcontainers dependencies are available.
- Generated automation POMs use `maven-failsafe-plugin` and run Cucumber/Testara runners in `mvn verify`, matching the example projects.
- Generated `junit-platform.properties` defaults to stable serial execution, no retries, and `cucumber.junit-platform.naming-strategy=long`; enable parallelism/retry only when the project has proven engine isolation.
- Generated runners match the examples: `Junit5RunnerTests` is only `@Log4j2` + `@TestSuite`; detailed Cucumber config belongs in properties or `Junit4RunnerTests`.
- `test-plan` must ask for clarification when the request is generic or missing runnable context. Do not invent page, service, flow, selector, payload, or validation names just to produce output.
- `test-run` resolves explicit tags first, then project-indexed tags and feature/scenario text. Default multi-tag output is `and`; use `or` only when the user asks for an OR run. If no indexed feature/tag context exists, ask for `projectRoot`, an explicit tag, feature name, or scenario name.
- Avoid hardcoded sample domains. Derive feature names, tags, page/action names, request spec names, service aliases, and property keys from user input plus `testara_context`; normalize only for Java/package/property naming rules.

## Code conventions
- Reuse Testara built-ins before creating project code. Built-in steps, commands, validations, request specs, pages/actions, and helpers are preferred over custom Cucumber steps.
- Place reusable project Java under `src/main/java/{basePackage}`. Use singular package names that match the example projects: `page`, `action`, `command`, `validation`, `data`, `model`.
- Keep runners and glue-only test bootstrap under `src/test/java/{basePackage}/runner` and `src/test/java/{basePackage}/steps`.
- Use `src/test/resources/features` for Gherkin, `src/test/resources/files/{domain}/request` for API request specs, `src/test/resources/templates` for reusable payload/template files, and `src/test/resources/schemas` for JSON schemas.
- Do not create direct Selenium/Playwright/Appium drivers. Use Testara driver/session abstractions, built-in UI steps, `UserAction`, and `Locator` fields.
- Do not generate helper classes that duplicate `MapperHelper`, `TransformerService`, `CommandExecutor`, `ValidatorHelper`, `DataHolder`, `RestApiFacade`, `SqlHelper`, `MongoHelper`, `Kafka*Helper`, `ElasticSearchHelper`, `PageFinder`, or `Actor`.

## POM dependency scope rules
- Compile scope: any Testara module imported by Java under `src/main/java`. Do not write `<scope>test</scope>` for these.
- Compile scope: `testara-junit5` — matches the sample project; it provides runner annotations needed at compile and runtime.
- Test scope: Cucumber step modules (`testara-*-cucumber`) and runner/test bootstrap dependencies used only under `src/test/java` or feature execution.
- `lombok` scope: `provided` — annotation processor only, not needed at runtime.
- NEVER add `cucumber-junit-platform-engine` to generated POMs. It is not in the sample project and is not required; Testara provides its own JUnit Platform engine (`testara-cucumber`).
- Avoid explicit JUnit Platform versions in generated POMs unless the project already pins a compatible BOM; the `junit5` Maven profile uses `${junit-platform.version}` for `junit-platform-console-standalone`.
- Always include compile-scope `testara-command` and `testara-validation` when generating `{basePackage}.command` or `{basePackage}.validation`.
- API slice: `testara-api` compile, `testara-api-cucumber` test.
- UI slice: `testara-ui` and selected engine (`testara-ui-selenium`, `testara-ui-playwright`, or `testara-ui-appium`) compile; `testara-ui-cucumber` test.
- DB slice: `testara-database` compile; `testara-database-cucumber` test.
- Kafka slice: `testara-streaming` compile; `testara-streaming-cucumber` test.
- Elastic slice: `testara-elastic` compile; `testara-elastic-cucumber` test.
- Generated POM has two Maven profiles: `junit4` (default, uses `surefire-junit47` + `testara-reporter-plugin`) and `junit5` (opt-in, uses `includeJUnit5Engines: testara-cucumber`). Run with `mvn verify` (junit4) or `mvn verify -P junit5`.

## DataTable format rules
TransformerService processes DataTables. The required format depends on the target output type.

### Pattern 1 — vertical key-value `|key|value|`: use when the step builds a `Map` from multiple rows
The header row MUST be exactly `| key | value |`. Each subsequent row becomes one map entry.
Steps that use this: API headers, API form/query params, Mongo query (`select/delete/count/update/distinct`), Elastic query (`assign data ... with query`), UI proxy rules.
```gherkin
# API headers
And [api] prepare header with value
  | key           | value            |
  | Content-Type  | application/json |
  | Authorization | Bearer token123  |

# Mongo select query — fields: query, sort, project, limit, skip
When [mongo] select data with query :
  | key    | value                             |
  | query  | {"sku": "properties(test.x.sku)"} |
  | sort   | {"_id": -1}                       |
  | limit  | 1                                 |

# Elastic search query — fields: luceneQuery, routing, type, sortBy, from, size
When [elastic-search] assign data results from index products with query :
  | key         | value                              |
  | luceneQuery | {"term":{"id":"properties(x.id)"}} |
  | size        | 10                                 |
```
Without `|key|value|` headers, a 2-column table interprets row 0 as column identifiers and silently produces a wrong map.

### Pattern 2 — horizontal multi-column: use when each row is one object/record or binds a template
Row 0 = field/column names; each subsequent row = one record. For template binding, column names are JSONPath keys.
Steps that use this: `toList(Class)` targets (API cookies, multipart, Kafka batch), DataManipulationSteps, validation tables.
```gherkin
# Template binding — column names match template JSONPath keys
And [api] prepare request data payload from template "OrderTemplate" with value
  | id                           | amount                         |
  | properties(test.order.id)    | properties(test.order.amount)  |

# List target — column names match bean fields (CookieModel, MultiPartData, KafkaMessage)
And [api] prepare cookies with value
  | name | value                       | domain   |
  | sid  | properties(test.user.token) | .example |

# Validation table — fixed 3-column shape
Then [api] do these validations
  | actual                    | validation | expectation |
  | response($['orderId'])    | NOT_EMPTY  | true        |
  | response($['amount'])     | EQUALS     | 100         |
```

### Pattern 3 — horizontal single-row (for Elastic insert/update document)
Row 0 = field names, row 1 = values. The last row becomes the document map.
```gherkin
When [elastic-search] insert to index "products" with data :
  | name           | status | price |
  | Sample Product | active | 99.9  |
```

## Utilities and helpers
- `MapperHelper`: convert JSON/string/maps/lists to typed Java objects and serialize objects. Use inside commands, validators, DB/Kafka/Elastic helpers, and request/response conversions.
- `TransformerService`: convert Cucumber DataTables/templates into maps, lists, and typed objects. Use for all table-driven data; remember Testara mapped tables are horizontal.
- `CommandExecutor`: evaluate command expressions such as `properties(key)`, `random(6,NUMERIC)`, `request($['alias'])`, and `response($['alias'])`. Use this instead of hand-parsing command syntax.
- `DataHolder` / request and response stores: keep scenario state through aliases assigned by built-in API/DB/Elastic/Kafka steps. Prefer aliases and command expressions over static fields.
- `ValidatorHelper`: execute built-in and project validators from validation tables. Use `see that` / `do these validations` tables before writing custom assertions.
- `ClassScanner`: discovers commands, validations, pages, and actions from configured scan locations. Always generate scan-location properties with built-in and project packages.
- `RestApiFacade`: low-level API execution facade behind `ApiBaseSteps`. Prefer request spec JSON plus `[api] process request to ...`; use facade only in advanced custom Java.
- `SqlHelper` and `MongoHelper`: database helpers behind SQL/Mongo steps and DB commands. Prefer `[sql]` / `[mongo]` built-in steps; use helpers for custom Java only.
- `KafkaConsumerHelper` and `KafkaPublisherHelper`: streaming helpers behind Kafka steps. Prefer built-in producer/consumer steps unless custom polling/filtering logic is truly needed.
- `ElasticSearchHelper`: Elastic helper behind `[elastic-search]` steps. Prefer built-in Elastic steps for CRUD/search/count/index assertions.
- `PageFinder`, `Element`, `Locator`, `Actor`, and `UserAction`: UI abstractions. Page classes expose `Locator` fields; actions call `attemptsTo(...)`; features call UIBaseSteps or `user do "..."`

## Built-in step usage
- API: use `[api]` steps for service selection, headers/cookies/path/query/form/body setup, request execution, response assignment, status/success/error assertions, response-time assertions, and load-test flows.
- UI: use UIBaseSteps for driver startup, page navigation, typing/clicking/waiting/asserting. Use `chrome`, `firefox`, `safari`, or `edge` as driver names; do not use `web`.
- DB: use `[sql]` steps for SQL connection/query/assignment and `[mongo]` steps for collection selection, query/select/delete/update/count/aggregate/distinct.
- Kafka: use producer/consumer steps for starting services, sending messages, listening to topics, assigning latest/count/filter results, and stopping clients.
- Elastic: use `[elastic-search]` steps for connection, index existence, insert/update/get/delete, search/count, and assigning previous responses.
- Generic validation: use `see that` or `do these validations` with `actual | validation | expectation` tables before writing custom step assertions.

## Helper step command validation decisions
- Helper: use framework helpers from Java when extending behavior inside a project command, validation, page action, or rare custom step. Examples: `RestApiFacade` for advanced API calls, `SqlHelper`/`MongoHelper` for custom DB utility code, `KafkaConsumerHelper`/`KafkaPublisherHelper` for advanced streaming code, `ElasticSearchHelper` for advanced search/index code, `MapperHelper` and `TransformerService` for object/table/template conversion.
- Step: use built-in Cucumber steps directly in feature files for scenario flow and observable behavior. Prefer steps when the action already exists and the feature remains readable.
- Command: create a project command under `{basePackage}.command` only when a reusable expression is needed inside feature values, request specs, validations, or other commands. Commands should return a value and be referenced as `commandName(arg1,arg2)`. Always keep `command.executor.scan-locations=io.github.ygrip.testara,{basePackage}.command`.
- Validation: create a project validation under `{basePackage}.validation` only when the built-in validation keywords cannot express the assertion. Validations should be called from `see that` / `do these validations` tables, not through custom step assertions. Always keep `validator.helper.scan-locations=io.github.ygrip.testara,{basePackage}.validation`.
- Custom Cucumber step: last resort for domain language that coordinates multiple Testara primitives and cannot be represented as built-in steps plus command/validation/page/action artifacts. Keep custom steps glue-only under `src/test/java/{basePackage}/steps`.
- Request spec: for API, prefer JSON request specs over command/custom step code whenever the variation is request shape, params, payload, headers, cookies, multipart data, or reusable endpoint flow.
- Page/action: for UI, represent locators in Page classes and reusable flows in `UserAction` classes; do not create Selenium/Playwright helper wrappers.

## DB Kafka Elastic patterns
- SQL: configure `sql.service.{alias}.uri|username|password|dbType` or `hostName|port|dbName|username|password|dbType`; connect with `Given [sql] connect to database with name {alias}`; use `Given [sql] prepare query with value :` for multiline SQL; execute; assign with `Then [sql] assign previous database response to {alias}`. Use `sqlQuery(alias,query)` command only inside expressions/custom Java when built-in steps are not enough.
- Mongo: configure `mongo.service.{alias}.connectionString|dbName` or `hosts|dbName|username|password`; connect with `Given [mongo] connect to database with name {alias}`; select collection; DataTable query bodies use **Pattern 1** (`|key|value|` headers) — supported keys: `query`, `sort`, `project`, `limit`, `skip` (select), `query`+`useMany` (delete), `query`+`update`+`useMany` (update), `field`+`query` (distinct); assign with `Then [mongo] assign previous database response to {alias}`.
- Kafka producer: configure `kafka.service.{alias}.servers`; start with `Given user start kafka producer for {alias}`; send with `When user send kafka message to topic "properties(kafka.topic.{name})" with data "request($['event'])"` or key+data; always stop producer.
- Kafka consumer: start with `Given user start kafka consumer for {alias}`; listen with `Given user listen kafka from topic {topicAlias}`; assign count/latest/filter results; always stop consumer. Use filter tables instead of custom polling code.
- Elastic: configure `elasticsearch.service.{alias}.hosts[0]|username|password|secured|requireAuthentication`; connect with `Given [elastic-search] connect to elastic search with name {alias}`; search/assign uses **Pattern 1** (`|key|value|` headers) — supported keys: `luceneQuery`, `routing`, `type`, `sortBy`, `from`, `size`; insert/update uses **Pattern 3** (horizontal single-row document); assign with `Then [elastic-search] assign previous elastic search response to {alias}`.
- Use `${ENV:fallback}` for generated DB/Kafka/Elastic config values. Use `properties(...)` only inside features/request specs when reading application properties.

## RULE 1: property values and dynamic values
Property files MUST NOT use `properties(other.key)` as a value. Use `${ENV:fallback}` for URLs, hosts, ports, credentials, topic names, and DB names in generated config.
Features/request specs MAY use `properties(key)` for values stored in `application.properties`.
Use command expressions (`uuid()`, `random(...)`, `oneOf(...)`, `timestamp()`) for generated dynamic values instead of creating static properties.
ALLOWED hardcoded: HTTP status codes (200/400), booleans (true/false), stable enums.
NEVER: localhost, http://, or credentials directly in feature files.

Example:
  WRONG: Given [api] prepare header "X-Token" with value "abc123"
  RIGHT: Given [api] prepare header "X-Token" with value "properties(test.user.token)"

## RULE 2: request spec for non-trivial API requests
If request has payload, path params, headers, query params, cookies, multipart data, logging controls, or is reused -> generate
  src/test/resources/files/{domain}/request/{flow}.json
  and use: When [api] process request to "files/{domain}/request/{flow}"
Direct step only for simple GET without params.

Request spec JSON maps to `CreateRequestSpecification`:
  specification, httpMethod, url, contentType, cookies, queryParameters,
  formParameters, headers, pathParameters, multiPartData, payload,
  requestLog, responseLog, autoCloseConnection.
Use command expressions inside specs: `properties(key)`, `request($['alias'])`,
`response($['alias'])`, `random(10,NUMERIC)`, `uuid()`.

## RULE 3: UserAction for reusable UI flows
If UI scenario has 3+ operations on the same page -> generate UserAction class
  @OnPage(value = {LoginPage.class}) + @Action("action name")
  public class LoginActions extends UserAction
  Feature:
    When user do "action name" in "page" page with parameter
      |key|value|
      | username | properties(test.user.email)    |
      | password | properties(test.user.password) |
Not a custom Cucumber step.
Never group different pages into nested static UserAction classes. Generate separate top-level files such as `CheckoutInfoActions.java` and `CheckoutOverviewActions.java`.
For truly shared/global actions, use a top-level class with `@Action(value = "open cart", allowAnonymousCall = true)` so it can be resolved without binding to one current page.

## RULE 4: Page URL in properties
  @Page(name = "login", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
  public class LoginPage extends SeleniumPage or PlaywrightPage
  private static final Locator USERNAME_FIELD = Locator.id("user-name")
  web.page.desktop.login.url=${APP_WEB_LOGIN_URL:http://localhost:3000/login}

## RULE 5: service config before features
Generate api.service.{name}.* and spec.api.{name}.* in configuration.properties
BEFORE generating feature files that reference that service.

## RULE 6: DB config pattern
  sql.service.{name}.uri=${DB_NAME_URI:jdbc:postgresql://localhost:5432/testdb}
  mongo.service.{name}.connectionString=${MONGO_NAME_URI:mongodb://localhost:27017}
  kafka.service.{name}.servers=${KAFKA_NAME_SERVERS:localhost:9092}
User-defined values used by features go in application.properties/application-{env}.properties.

## RULE 7: command scan locations
  class.loader.default-scan-locations=io.github.ygrip.testara,{basePackage}
  command.executor.scan-locations=io.github.ygrip.testara,{basePackage}.command
  validator.helper.scan-locations=io.github.ygrip.testara,{basePackage}.validation
Always include io.github.ygrip.testara to include built-ins.

## RULE 8: step priority
1. Testara built-in steps ([api], [sql], [mongo], [kafka], UIBaseSteps)
2. Project-specific steps
3. Extension artifact (command, validation, request spec, page, action)
4. Custom Cucumber step — LAST RESORT ONLY

## RULE 9: compile after init
After test-init --write, run mvn test-compile to verify the scaffold compiles.

## RULE 10: scan locations on new artifact
When generating a new Command or Validator class, verify command.executor.scan-locations
and validator.helper.scan-locations in configuration.properties include the target package.

## RULE 11: interactive planning
If the user request is too broad (`test the app`, `create UI test`, `run checkout`) return `needs_input` with the smallest useful questions. Ask for API method/service/path/expected validation, UI page/action/expected state/selectors, or DB/Kafka/Elastic alias/query/topic/index context. Do not fall back to Login/Home/Page/StepDefinitions placeholders.

## RULE 12: dynamic run filters
For `testara_run`, convert user text to the narrowest tag expression supported by the project index. Exact `@tags` win. Indexed tag words map to tags. Scenario/feature text maps to the matching scenario's feature + scenario tags joined with `and`. Return clarification when the index is empty or the expression matches zero scenarios.

## UI Runtime Quirks — Read Before Generating UI Features

These are confirmed framework behaviors that cause debugging loops when not respected upfront.

### Quirk 1: Element name resolution uses Locator field names exactly
`user should see "X" is displayed`, `user click the "X"`, and `Enter.text(...).into("X")` all resolve "X" by
converting it to SCREAMING_SNAKE_CASE and looking up a matching `Locator` field on the current page class.
- `"username field"` → `USERNAME_FIELD` ✅
- `"cart item"` → `CART_ITEM` ✅ (only if `CART_ITEM` exists and the locator matches a real element)
- `"primary action"` → `PRIMARY_ACTION` — **only works if that locator targets a real visible element**

**Rule:** Before using `user should see "X"`, confirm the page class has a Locator field that maps to X AND that locator points to an element that is actually visible after the preceding steps.

### Quirk 2: Page context does not auto-advance after UserAction
After `When user do "add item to cart" in "inventory" page`, the page context is still `inventory`, NOT `cart`.
`Then user is in "cart" page` will fail if the action only clicked an add-to-cart button — no page navigation occurred.

**Rule:** To land on a different page after a UserAction, emit an explicit navigation step:
```gherkin
When user open "cart" page          # URL navigation — always works
# NOT: Then user is in "cart" page  # fails after click-only UserAction
```

### Quirk 3: Do not enable Cucumber retry for UI
`cucumber.max.retry.failed.scenarios > 0` is not safe for UI tests — Testara manages its own session lifecycle and retry support may be added at the framework level in a future version.

**Rule:** Always keep `cucumber.max.retry.failed.scenarios=0`. Do not suggest enabling it.

### Quirk 4: Parallel execution registers duplicate engines
`cucumber.execution.parallel.enabled=true` causes multiple Selenium/Playwright engines to register under the same ID.

**Rule:** Keep `cucumber.execution.parallel.enabled=false` for UI projects. Both are already set correctly by `testara_init` — do not override them.

### Quirk 6: Screenshot hook crashes before browser opens
`automation.engine.screenshot-strategy=ON_EACH_STEP` attempts a screenshot before every step — including the very first step that opens the browser. If `TestContext` is not yet initialized, this throws `IllegalStateException`. Change to `ON_FAIL` (screenshot only on failure) or `NONE` (disabled) in `configuration.properties` to avoid this. `ON_EACH_STEP` requires that TestContext and the browser session are already available before the hook fires.

### Quirk 5: testara_run and testara_context require projectRoot when MCP is launched globally
When the MCP server starts from outside the project workspace (e.g. `~/`), the project index is empty.
`testara_run` will show `indexed-features: 0` and fail to resolve any tags.

**Rule:** Pass `projectRoot` to every tool call after a global-scope MCP launch:
```
testara_context(projectRoot="/path/to/project")
testara_run(input="...", projectRoot="/path/to/project")
```

## Quick reference: key steps by slice
API:    Given [api] using service with alias {name} | [api] prepare pathParam for id with value "properties(test.{domain}.id)" | When [api] process request to "files/{domain}/request/{flow}" | Then [api] response statusCode should be 200 | [api] assign previous response data to {alias}
UI:     Given user using chrome in desktop | When user open "{page}" page | Then user is in "{page}" page | When user do "{action}" in "{page}" page with parameter | Then user see that (actual | validation | expectation)
SQL:    Given [sql] connect to database with name {name}Db | [sql] prepare query with value : | When [sql] execute database query | Then [sql] assign previous database response to {alias}Rows
Mongo:  Given [mongo] connect to database with name {name}Db | [mongo] select collection with name {col} | When [mongo] select data with query : (|key|value| table — keys: query/sort/project/limit/skip) | Then [mongo] assign previous database response to {alias}Rows
Kafka:  Given user start kafka producer for {name} | When user send kafka message to topic "properties(kafka.topic.{alias})" with data "request($['event'])" | Then user stop kafka producer
Elastic: Given [elastic-search] connect to elastic search with name {name} | When [elastic-search] assign data {alias} from index {index} with query : (|key|value| table — keys: luceneQuery/routing/type/sortBy/from/size) | Then [elastic-search] assign previous elastic search response to {alias}
