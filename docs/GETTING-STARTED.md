# Getting Started with Testara

A hands-on path from empty directory to a running cross-layer test. For the design behind
these pieces read **[ARCHITECTURE.md](ARCHITECTURE.md)**; for conventions read
**[BEST-PRACTICES.md](BEST-PRACTICES.md)**.

---

## Prerequisites

- **Java 21+**
- **Maven 3.9+**

## 1. Add Testara to a project

Import the BOM (aligns every module version), then declare only the slices you need:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.ygrip</groupId>
      <artifactId>testara-bom</artifactId>
      <version>${testara.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- BDD steps for the slices you test — each -cucumber pulls in its capability module -->
  <dependency><groupId>io.github.ygrip</groupId><artifactId>testara-api-cucumber</artifactId></dependency>
  <dependency><groupId>io.github.ygrip</groupId><artifactId>testara-ui-cucumber</artifactId></dependency>
  <dependency><groupId>io.github.ygrip</groupId><artifactId>testara-ui-selenium</artifactId></dependency>
  <!-- The runner -->
  <dependency><groupId>io.github.ygrip</groupId><artifactId>testara-junit5</artifactId></dependency>
</dependencies>
```

> **Fastest path:** the **Testara Agent** scaffolds all of this for you — see §7.

## 2. Wire Cucumber to Testara

In `src/test/resources/junit-platform.properties`:

```properties
cucumber.object-factory=io.github.ygrip.testara.cucumber.factory.TestaraObjectFactory
cucumber.glue=io.github.ygrip.testara
```

Always keep `io.github.ygrip.testara` in the glue / scan locations so the built-in steps,
commands, and components are discovered.

## 3. Configuration lives in properties, never in steps

Create `src/test/resources/application.properties` (or per-environment files). Everything
environment-specific goes here and is referenced from features with `properties(...)`.

```properties
# API service config (prefix: api.service.<name>.*)
api.service.user-api.host=${properties(user-api.host)}
api.service.user-api.basePath=/api/v1
api.service.user-api.header.Authorization=Bearer ${properties(user-api.token)}

# The actual environment values (swap per env)
user-api.host=https://staging.example.com
user-api.token=abc123

# UI page URLs (prefix: web.page.<device>.<name>.url)
web.page.desktop.login.url=https://staging.example.com/login
web.page.desktop.inventory.url=https://staging.example.com/inventory
```

## 4. Write a feature — API example

`src/test/resources/features/user/get-user.feature`:

```gherkin
Feature: User lookup

  Scenario: fetch an existing user
    Given John using service with alias user-api
    When  John process request to "user/get-user"
    Then  John response statusCode should be 200 and success should be true
```

The request spec `src/test/resources/user/get-user.json`:

```json
{
  "specification": "user-api",
  "httpMethod": "GET",
  "url": "/users/{id}",
  "pathParameters": { "id": "properties(test.userId)" },
  "responseLog": ["BODY"]
}
```

No custom Java needed — `ApiBaseSteps` provides every step above.

## 5. Write a feature — UI example

```gherkin
Feature: Login

  Scenario: successful login
    Given user using chrome in desktop
    When  user open "login" page
    And   user enter value "properties(login.username)" on "username field"
    And   user enter value "properties(login.password)" on "password field"
    And   user click the "login button"
    Then  user should see "inventory container" is displayed
```

Define the page object (URL comes from `web.page.desktop.login.url`, set in §3):

```java
@Page(name = "login", platforms = {DeviceType.DESKTOP})
public class LoginPage extends SeleniumPage {
  @FindBy("#user-name")        Element usernameField;   // "username field"
  @FindBy("#password")         Element passwordField;   // "password field"
  @FindBy("#login-button")     Element loginButton;     // "login button"
}
```

For multi-step flows on one page, encapsulate them in a `UserAction` task instead of repeating
steps:

```java
@OnPage(LoginPage.class)
public class LoginActions extends UserAction {
  @Action("login as {username} / {password}")
  public void login(String username, String password) {
    attemptsTo(
      Enter.text(username).into("#user-name"),
      Enter.text(password).into("#password"),
      Click.on("#login-button"));
  }
}
```

```gherkin
When user do "login as properties(login.username) / properties(login.password)" in "login" page
```

## 6. Cross-layer example (the reason Testara exists)

```gherkin
Scenario: order creation is persisted and published
  Given John using service with alias order-api
  When  John process request to "order/create-order"
  Then  John response statusCode should be 201

  Given [sql] connect to database with name mainDb
  And   [sql] prepare query with value "SELECT status FROM orders WHERE id = response(id)"
  When  [sql] execute database query
  Then  [sql] assign previous database response to dbOrder
  And   user data "dbOrder.status" should be equal to "CREATED"

  Given user start kafka consumer for orderService
  When  user assign 1 latest records from topic "order.created" to event
  Then  user data "event.orderId" should be equal to response(id)
```

One scenario, three slices, one shared `DataHolder` (`response(id)` flows from the API call into
the SQL query and the Kafka assertion).

## 7. Fast path — the Testara Agent

```sh
# Install (also auto-configures MCP for VS Code / Cursor / Claude Desktop / Claude Code)
curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash

# 1. Scaffold a project (interactive: group id, artifact, type)
mkdir my-tests && cd my-tests
testara-agent test-init

# 2. Generate a Testara-flavor feature (uses properties() + request specs automatically)
testara-agent test-plan 'test the payment refund approval' --write

# 3. Run it
TESTARA_AGENT_RUN_ENABLED=true testara-agent test-run 'payment refund' --execute
```

Generated features report a **Testara Flavor Score** (% of steps using built-in steps) and a
**Runtime Context Score** (% of values using `properties()`), and guardrails block hardcoded
URLs/credentials before output. Full reference: **[agentic-skills.md](agentic-skills.md)**.

## 8. Running tests directly with Maven

```bash
# All scenarios
mvn test

# By tag
mvn test -Dcucumber.filter.tags="@smoke and @api"
```

## Where to go next

| I want to… | Read |
|---|---|
| Understand how it fits together | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Write idiomatic, portable tests | [BEST-PRACTICES.md](BEST-PRACTICES.md) |
| See every built-in command | [../testara-command/README.md](../testara-command/README.md) |
| See every validator | [../testara-validation/README.md](../testara-validation/README.md) |
| Deep-dive API testing | [../testara-api/README.md](../testara-api/README.md) |
| Deep-dive UI testing | [../testara-ui/README.md](../testara-ui/README.md) |
| Use the AI agent / MCP | [agentic-skills.md](agentic-skills.md) |
