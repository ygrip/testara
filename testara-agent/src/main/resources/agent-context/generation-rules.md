# Testara Agent Generation Rules

## Testara runtime chain
properties → command conversion → config binding → base steps → request specs → pages/actions → screenplay → compile gate

## RULE 1: properties() for env-specific values
MUST use properties(key) for: URLs, hosts, ports, emails, passwords, tokens,
topic names, DB names, credentials, test data IDs, reusable test constants.
ALLOWED hardcoded: HTTP status codes (200/400), booleans (true/false), stable enums.
NEVER: localhost, http://, or credentials directly in feature files.

Example:
  WRONG: Given [api] prepare header "X-Token" with value "abc123"
  RIGHT: Given [api] prepare header "X-Token" with value "properties(test.user.token)"

## RULE 2: request spec for non-trivial API requests
If request has payload, path params, headers, query params, or is reused → generate
  src/test/resources/files/{domain}/request/{flow}.json
  and use: When [api] process request to "files/{domain}/request/{flow}"
Direct step only for simple GET without params.

## RULE 3: UserAction for reusable UI flows
If UI scenario has 3+ operations on the same page → generate UserAction class
  @OnPage(LoginPage.class) + @Action("action name")
  Feature: When user do "action name" in "page" page with parameter | username | properties(test.user.email) |
Not a custom Cucumber step.

## RULE 4: Page URL in properties
  @Page(name = "login", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
  web.page.desktop.login.url=properties(app.web.login-url)

## RULE 5: service config before features
Generate api.service.{name}.* and spec.api.{name}.* in configuration.properties
BEFORE generating feature files that reference that service.

## RULE 6: DB config pattern
  sql.service.{name}.host-name=properties(db.{name}.host)
  mongo.service.{name}.hosts=properties(db.{name}.hosts)
  kafka.service.{name}.servers=properties(kafka.{name}.servers)
Values (host, port, credentials) go as separate properties: db.{name}.host=localhost

## RULE 7: command scan locations
  command.executor.scan-locations=io.github.ygrip.testara,{basePackage}.commands
  validator.helper.scan-locations=io.github.ygrip.testara,{basePackage}.validations
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
UI:     Given user using web in desktop | When user open "{page}" page | When user do "{action}" in "{page}" page with parameter | Then user is in "{page}" page | user should see "{element}" is displayed
SQL:    Given [sql] connect to database with name {name}Db | [sql] prepare query with value : | When [sql] execute database query | Then [sql] assign previous database response to {alias}Rows
Mongo:  Given [mongo] connect to database with name {name}Db | [mongo] select collection with name {col} | When [mongo] select data with query : | Then [mongo] assign previous database response to {alias}Rows
Kafka:  Given [kafka] start kafka producer for {name} | When [kafka] send kafka message to topic "properties(kafka.topic.{alias})" | Then [kafka] stop kafka producer
