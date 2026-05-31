# Testara Agent Generation Rules

## Testara runtime chain
properties -> command conversion -> config binding -> base steps -> request specs -> pages/actions -> screenplay -> compile gate

## Code conventions
- Reuse Testara built-ins before creating project code. Built-in steps, commands, validations, request specs, pages/actions, and helpers are preferred over custom Cucumber steps.
- Place reusable project Java under `src/main/java/{basePackage}`. Use singular package names that match the example projects: `page`, `action`, `command`, `validation`, `data`, `model`.
- Keep runners and glue-only test bootstrap under `src/test/java/{basePackage}/runner` and `src/test/java/{basePackage}/steps`.
- Use `src/test/resources/features` for Gherkin, `src/test/resources/files/{domain}/request` for API request specs, `src/test/resources/templates` for reusable payload/template files, and `src/test/resources/schemas` for JSON schemas.
- Do not create direct Selenium/Playwright/Appium drivers. Use Testara driver/session abstractions, built-in UI steps, `UserAction`, and `Locator` fields.
- Do not generate helper classes that duplicate `MapperHelper`, `TransformerService`, `CommandExecutor`, `ValidatorHelper`, `DataHolder`, `RestApiFacade`, `SqlHelper`, `MongoHelper`, `Kafka*Helper`, `ElasticSearchHelper`, `PageFinder`, or `Actor`.

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
- SQL: configure `sql.service.{alias}.host-name|db-name|username|password|db-type`; connect with `Given [sql] connect to database with name {alias}`; use `Given [sql] prepare query with value :` for multiline SQL; execute; assign with `Then [sql] assign previous database response to {alias}`. Use `sqlQuery(alias,query)` command only inside expressions/custom Java when built-in steps are not enough.
- Mongo: configure `mongo.service.{alias}.hosts|db-name|username|password`; connect with `Given [mongo] connect to database with name {alias}`; select collection; DataTable query bodies use **Pattern 1** (`|key|value|` headers) — supported keys: `query`, `sort`, `project`, `limit`, `skip` (select), `query`+`useMany` (delete), `query`+`update`+`useMany` (update), `field`+`query` (distinct); assign with `Then [mongo] assign previous database response to {alias}`.
- Kafka producer: configure `kafka.service.{alias}.servers`; start with `Given user start kafka producer for {alias}`; send with `When user send kafka message to topic "properties(kafka.topic.{name})" with data "request($['event'])"` or key+data; always stop producer.
- Kafka consumer: start with `Given user start kafka consumer for {alias}`; listen with `Given user listen kafka from topic {topicAlias}`; assign count/latest/filter results; always stop consumer. Use filter tables instead of custom polling code.
- Elastic: configure `elastic-search.service.{alias}.host|port|scheme`; connect with `Given [elastic-search] connect to elastic search with name {alias}`; search/assign uses **Pattern 1** (`|key|value|` headers) — supported keys: `luceneQuery`, `routing`, `type`, `sortBy`, `from`, `size`; insert/update uses **Pattern 3** (horizontal single-row document); assign with `Then [elastic-search] assign previous elastic search response to {alias}`.
- Use `properties(...)` for hosts, ports, credentials, topic names, index names, document IDs, and query literals that vary by environment.

## RULE 1: properties() for env-specific values
MUST use properties(key) for: URLs, hosts, ports, emails, passwords, tokens,
topic names, DB names, credentials, test data IDs, reusable test constants.
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
  Feature:
    When user do "action name" in "page" page with parameter
      | username                    | password                       |
      | properties(test.user.email) | properties(test.user.password) |
Not a custom Cucumber step.

## RULE 4: Page URL in properties
  @Page(name = "login", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
  public class LoginPage extends SeleniumPage or PlaywrightPage
  private static final Locator USERNAME_FIELD = Locator.id("user-name")
  web.page.desktop.login.url=properties(app.web.login-url)

## RULE 5: service config before features
Generate api.service.{name}.* and spec.api.{name}.* in configuration.properties
BEFORE generating feature files that reference that service.

## RULE 6: DB config pattern
  sql.service.{name}.host-name=properties(db.{name}.host)
  mongo.service.{name}.connectionString=properties(mongo.{name}.connection-string)
  kafka.service.{name}.servers=properties(kafka.{name}.servers)
Values (host, port, credentials) go as separate properties: db.{name}.host=localhost

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

## Quick reference: key steps by slice
API:    Given [api] using service with alias {name} | [api] prepare pathParam for id with value "properties(test.{domain}.id)" | When [api] process request to "files/{domain}/request/{flow}" | Then [api] response statusCode should be 200 | [api] assign previous response data to {alias}
UI:     Given user using chrome in desktop | When user open "{page}" page | Then user is in "{page}" page | When user do "{action}" in "{page}" page with parameter | Then user should see "{element}" is displayed
SQL:    Given [sql] connect to database with name {name}Db | [sql] prepare query with value : | When [sql] execute database query | Then [sql] assign previous database response to {alias}Rows
Mongo:  Given [mongo] connect to database with name {name}Db | [mongo] select collection with name {col} | When [mongo] select data with query : (|key|value| table — keys: query/sort/project/limit/skip) | Then [mongo] assign previous database response to {alias}Rows
Kafka:  Given user start kafka producer for {name} | When user send kafka message to topic "properties(kafka.topic.{alias})" with data "request($['event'])" | Then user stop kafka producer
Elastic: Given [elastic-search] connect to elastic search with name {name} | When [elastic-search] assign data {alias} from index {index} with query : (|key|value| table — keys: luceneQuery/routing/type/sortBy/from/size) | Then [elastic-search] assign previous elastic search response to {alias}
