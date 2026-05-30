# Testara Agentic Skill Integration Plan

## 1. Executive Summary

This document defines a plan to integrate an **agentic skill layer** into Testara.

The goal is to provide developer-facing skills that can analyze, review, generate, bootstrap, and execute Testara-friendly automation assets:

- `/test-summary`
- `/test-review`
- `/test-plan`
- `/test-command`
- `/test-validation`
- `/test-init`
- `/test-overview`
- `/test-run`

The recommended architecture is to build this as a **developer assistance toolkit**, not as part of the normal test execution runtime. The agent should inspect the project, understand Testara conventions, produce structured plans or patches, and only write files or execute tests when explicitly requested.

---

## 2. Current Repository Context

Testara is a **Java 21 multi-module Maven framework**.

Relevant existing modules include:

| Module | Purpose |
|---|---|
| `testara-core` | Foundation utilities, configuration, scanning, converters |
| `testara-command` | Command parser and expression engine |
| `testara-validation` | Assertion and validation framework |
| `testara-api` | REST API testing support |
| `testara-ui` | Engine-agnostic UI automation abstraction |
| `testara-cucumber` | Cucumber BDD integration |
| `testara-junit5` | JUnit 5 integration |
| `testara-reporter` | Report rendering |
| `testara-reporter-plugin` | Maven report generation plugin |
| `testara-*-cucumber` | Technology-specific Cucumber step modules |

The agentic layer should follow the existing modular style and avoid increasing the memory footprint of runtime modules.

---

## 3. Recommended Target Architecture

Add these modules:

```text
testara-agent
testara-agent-cli
testara-agent-mcp          # optional, later
testara-agent-spring       # optional, later
```

Recommended first release:

```text
testara-agent
testara-agent-cli
```

### 3.1 Why Separate Modules?

The agentic features are mainly used during authoring, review, planning, maintenance, and targeted execution of tests. They should not be loaded during normal automation execution unless explicitly used.

This keeps:

- `testara-core` lightweight
- runtime test execution clean
- agent dependencies optional
- LLM/network concerns isolated
- CLI/MCP integration independently versioned

---

## 4. Module Responsibilities

## 4.1 `testara-agent`

Core engine. No CLI, no web server.

Responsibilities:

```text
- Project indexing
- Feature file parsing
- Step definition discovery
- Command catalog extraction
- Validation catalog extraction
- Testara project structure detection
- Tag and scenario metadata analysis
- Prompt/context assembly
- LLM provider abstraction
- Structured output validation
- Patch proposal generation
- Test run command planning
- Test result summarization
```

Suggested package layout:

```text
io.github.ygrip.testara.agent
├── command
│   ├── AgentCommand.java
│   ├── AgentCommandRegistry.java
│   └── AgentCommandContext.java
├── index
│   ├── ProjectIndexer.java
│   ├── FeatureIndex.java
│   ├── StepDefinitionIndex.java
│   ├── CommandIndex.java
│   ├── ValidationIndex.java
│   └── TestaraProjectProfile.java
├── parser
│   ├── FeatureParser.java
│   ├── GherkinModel.java
│   ├── JavaStepParser.java
│   ├── PomAnalyzer.java
│   └── CucumberReportParser.java
├── runner
│   ├── TestRunPlanner.java
│   ├── TestRunExecutor.java
│   ├── TestRunResult.java
│   ├── TagFilterResolver.java
│   └── TestRunReportBuilder.java
├── skill
│   ├── TestSummarySkill.java
│   ├── TestReviewSkill.java
│   ├── TestPlanSkill.java
│   ├── TestCommandSkill.java
│   ├── TestValidationSkill.java
│   ├── TestInitSkill.java
│   ├── TestOverviewSkill.java
│   └── TestRunSkill.java
├── llm
│   ├── LlmClient.java
│   ├── LlmRequest.java
│   ├── LlmResponse.java
│   ├── OpenAiLlmClient.java
│   └── LocalLlmClient.java
├── output
│   ├── AgentResult.java
│   ├── FilePatch.java
│   ├── GeneratedFeature.java
│   ├── GeneratedJavaClass.java
│   └── AgentReport.java
└── safety
    ├── OutputValidator.java
    ├── FeaturePlacementGuard.java
    ├── JavaCompilationGuard.java
    ├── SecretRedactionGuard.java
    └── TestExecutionGuard.java
```

## 4.2 `testara-agent-cli`

Command-line wrapper around `testara-agent`.

Example usage:

```bash
testara-agent /test-summary src/test/resources/features/login.feature
testara-agent /test-review src/test/resources/features
testara-agent /test-plan --slice api --input "payment refund flow"
testara-agent /test-command "generate customer id with prefix and timestamp"
testara-agent /test-validation "validate response contains active users sorted by createdDate desc"
testara-agent /test-init --type api --base-package com.company.automation
testara-agent /test-overview .
testara-agent /test-run "run payment smoke tests"
```

## 4.3 `testara-agent-mcp`

Optional future module for exposing Testara skills as MCP tools.

Suggested MCP tool names:

```text
testara.test_summary
testara.test_review
testara.test_plan
testara.test_command
testara.test_validation
testara.test_init
testara.test_overview
testara.test_run
```

---

## 5. Agent Execution Modes

Support four modes:

```java
public enum AgentMode {
  READ_ONLY,
  PLAN,
  PATCH,
  APPLY
}
```

| Mode | Behavior | Used By |
|---|---|---|
| `READ_ONLY` | Analyze only, never modify files or execute tests | `/test-summary`, `/test-overview` |
| `PLAN` | Produce recommendations, placements, or run commands | `/test-review`, `/test-plan`, `/test-init`, `/test-run --dry-run` |
| `PATCH` | Produce structured file patches or unified diff | generation skills |
| `APPLY` | Write files or execute commands | explicit `--apply` or `/test-run` confirmation |

Default for generation skills should be **non-mutating**.

For `/test-run`, default behavior can be execution-enabled only if the CLI is run locally and the command is explicit. For interactive/MCP/IDE mode, prefer `--dry-run` first unless configured otherwise.

---

## 6. Core Data Models

## 6.1 `AgentSkill`

```java
public interface AgentSkill<I, O> {
  String name();
  O execute(I input, AgentCommandContext context);
}
```

## 6.2 `AgentCommandContext`

```java
public record AgentCommandContext(
    Path projectRoot,
    TestaraProjectProfile profile,
    AgentMode mode,
    LlmClient llmClient,
    AgentOptions options
) {}
```

## 6.3 `AgentResult`

```java
public record AgentResult(
    String summary,
    List<String> warnings,
    List<FilePatch> patches,
    Map<String, Object> metadata
) {}
```

## 6.4 `FilePatch`

```java
public record FilePatch(
    Path path,
    FilePatchOperation operation,
    String content,
    String reason
) {}
```

```java
public enum FilePatchOperation {
  CREATE,
  UPDATE,
  DELETE
}
```

## 6.5 `TestaraProjectProfile`

```java
public record TestaraProjectProfile(
    Path projectRoot,
    BuildTool buildTool,
    String javaVersion,
    List<String> mavenModules,
    List<Path> featureRoots,
    List<Path> requestSpecRoots,
    List<Path> validationRoots,
    List<FeatureIndex> features,
    List<StepDefinitionIndex> stepDefinitions,
    List<CommandIndex> commands,
    List<ValidationIndex> validations,
    Map<String, String> properties,
    Map<String, Object> conventions
) {}
```

---

## 7. Context Indexing Strategy

The indexer is the most important part of this feature. The agent must be grounded in the repository and must not hallucinate unavailable steps, commands, validations, tags, or file structure.

## 7.1 `ProjectIndexer`

Input:

```java
Path projectRoot;
```

Output:

```java
TestaraProjectProfile
```

Responsibilities:

```text
- Detect Maven modules
- Detect Testara dependencies
- Detect Java version
- Locate feature roots
- Locate test resource roots
- Locate request spec JSON files
- Locate validation JSON files
- Locate Cucumber step definition classes
- Locate custom command classes
- Locate custom validator classes
- Locate page objects
- Locate configuration files
- Build tag/placement/naming conventions
- Detect runnable Maven modules
- Detect Cucumber report output paths
```

## 7.2 Feature Indexing

Use Cucumber/Gherkin parser instead of regex where possible.

```java
public record FeatureIndex(
    Path path,
    String featureName,
    List<String> tags,
    List<ScenarioIndex> scenarios,
    List<StepIndex> backgroundSteps
) {}
```

```java
public record ScenarioIndex(
    String name,
    ScenarioType type,
    List<String> tags,
    List<StepIndex> steps,
    List<ExamplesIndex> examples
) {}
```

## 7.3 Step Definition Indexing

Scan Java files for Cucumber annotations:

```java
@Given(...)
@When(...)
@Then(...)
@And(...)
@But(...)
```

Store:

```java
public record StepDefinitionIndex(
    String annotation,
    String expression,
    Path sourcePath,
    String methodName,
    String className
) {}
```

## 7.4 Command Indexing

Detect built-in and custom commands:

```java
@CommandTag
CommandLogic<T>
```

Track:

```java
public record CommandIndex(
    String command,
    List<String> aliases,
    String returnType,
    boolean cacheable,
    Path sourcePath,
    String className
) {}
```

## 7.5 Validation Indexing

Detect built-in and custom validators:

```java
@ValidationTag
ValidatorLogic<ACTUAL, EXPECTED>
```

Track:

```java
public record ValidationIndex(
    String validation,
    List<String> aliases,
    String actualType,
    String expectedType,
    Path sourcePath,
    String className
) {}
```

## 7.6 Tag Indexing

Required for `/test-run`.

Track:

```java
public record TagIndex(
    String tag,
    int featureCount,
    int scenarioCount,
    List<Path> featurePaths,
    List<String> scenarioNames
) {}
```

The agent should infer tag meaning from:

```text
- Tag name, for example @smoke, @api, @ui, @payment
- Feature file path
- Feature title
- Scenario title
- Existing naming convention
```

---

## 8. Skill Design

# 8.1 `/test-summary`

## Goal

Summarize tests at:

```text
- Scenario level
- Feature file level
- Directory level
```

## Example Commands

```bash
testara-agent /test-summary src/test/resources/features/login.feature
testara-agent /test-summary src/test/resources/features/payment
testara-agent /test-summary src/test/resources/features/login.feature --scenario "Successful login"
```

## Output

```text
- Business behavior covered
- Preconditions/background
- Scenario list
- Tags
- Data tables/examples
- External fixtures used
- Commands used
- Validations used
- Risk/priority inference
- Gaps and ambiguous assertions
```

## Processing Flow

```text
1. Resolve path.
2. Parse `.feature` files.
3. Extract feature, background, scenarios, tags, steps, examples, data tables.
4. Detect Testara commands in step text.
5. Detect validation references if validation JSON files are used.
6. Use LLM only after deterministic extraction.
7. Return structured Markdown and JSON.
```

## Output Schema

```json
{
  "scope": "feature|scenario|directory",
  "path": "...",
  "summary": "...",
  "features": [],
  "scenarios": [],
  "tags": [],
  "fixtures": [],
  "commandsUsed": [],
  "validationsUsed": [],
  "risks": [],
  "gaps": []
}
```

---

# 8.2 `/test-review`

## Goal

Review a test path and detect:

```text
- Duplicated scenarios
- Overlapping coverage
- Inconsistent terminology
- Weak assertions
- Missing negative paths
- Excessive setup repetition
- Unstable/random data usage
- Inefficient feature organization
- Low-value or redundant tests
- Priority recommendations
```

## Example Commands

```bash
testara-agent /test-review src/test/resources/features/payment
testara-agent /test-review src/test/resources/features/login.feature
testara-agent /test-review src/test/java/com/company/steps
```

## Output

```text
- Findings grouped by severity
- Duplicate/near-duplicate scenarios
- Refactoring suggestions
- Suggested scenario priorities
- Suggested Background extraction
- Suggested Scenario Outline conversion
- Suggested command/validation reuse
- Optional patch proposal
```

## Severity Model

```text
BLOCKER  - scenario logically contradicts another
HIGH     - duplicate or unreliable test
MEDIUM   - inefficiency or unclear intent
LOW      - naming/style/organization
INFO     - optional enhancement
```

## Priority Model

```text
P0: critical smoke/blocking business flow
P1: core regression
P2: important edge/negative path
P3: low-risk variation
P4: redundant or candidate for removal
```

---

# 8.3 `/test-plan`

## Goal

Generate Testara-friendly Cucumber feature files from:

```text
- existing repo conventions
- existing feature files
- existing step definitions
- available Testara commands
- available validators
- user intent
```

The mode should be **interactive**. The agent may ask follow-up questions before generating final feature files.

## Example Commands

```bash
testara-agent /test-plan "Create tests for refund approval flow"
testara-agent /test-plan --interactive --slice api
testara-agent /test-plan --based-on src/main/openapi/payment.yaml
testara-agent /test-plan --target src/test/resources/features/payment/refund.feature
```

## Interactive Questions

```text
- Is this API, UI, database, streaming, or mixed flow?
- What is the target service/page/module?
- Should the output be smoke, regression, negative, or full coverage?
- Should generated data use Testara command expressions?
- Should request specs be created under src/test/resources/files?
- What tags should be used?
```

## Placement Strategy

```text
API:
src/test/resources/features/api/{domain}/{flow}.feature
src/test/resources/files/{domain}/{request-name}.json
src/test/resources/validations/{domain}/{validation-name}.json

UI:
src/test/resources/features/ui/{domain}/{flow}.feature
src/test/java/{basePackage}/pages/{Domain}Page.java
src/test/java/{basePackage}/actions/{Domain}Action.java

Database:
src/test/resources/features/database/{domain}/{flow}.feature

Streaming:
src/test/resources/features/streaming/{domain}/{flow}.feature
```

## Generated Feature Example

```gherkin
@api @refund @regression
Feature: Refund approval

  Background:
    Given the API service "payment-api" is available

  @P1 @positive
  Scenario: Approve a pending refund request
    Given a refund request exists with id "uuid()"
    When the user approves the refund request
    Then the refund approval response status should be 200
    And the refund status should be "APPROVED"
```

## Guardrails

```text
- Feature syntax is valid Gherkin
- Scenario names are unique in the target directory
- Generated steps either match known glue or are explicitly marked as missing
- Command expressions are syntactically valid
- Validation tags exist or generated validator is included
- Request spec JSON is valid
- No secrets are generated into files
```

---

# 8.4 `/test-command`

## Goal

Generate a Testara custom command from user description.

## Example Commands

```bash
testara-agent /test-command "generate a customer code with prefix CUS and current timestamp"
testara-agent /test-command "mask an email address but keep domain visible"
```

## Output

```text
- Java class implementing CommandLogic<T>
- @CommandTag
- Unit test
- Suggested package
- Required scan-location property
- Usage examples
```

## Generation Flow

```text
1. Parse user description.
2. Infer command name, aliases, return type, parameters, and cacheability.
3. Generate Java class.
4. Generate JUnit test.
5. Generate README usage snippet.
6. Suggest property update if package is outside scan location.
```

## Example Output Placement

```text
src/test/java/com/company/automation/commands/CustomerCodeCommand.java
src/test/java/com/company/automation/commands/CustomerCodeCommandTest.java
```

## Required Scan Config If Outside Default Package

```properties
command.executor.scan-locations=io.github.ygrip.testara,com.company.automation.commands
```

---

# 8.5 `/test-validation`

## Goal

Generate validation logic from user description.

Support two output modes:

```text
1. JSON validation file using existing validators
2. Java custom ValidatorLogic class when existing validators are insufficient
```

## Example Commands

```bash
testara-agent /test-validation "response should contain active users and every email should match valid email pattern"
testara-agent /test-validation "validate transaction amount is within tolerance of expected amount"
```

## Decision Logic

Use existing validation JSON when possible.

Generate custom Java validator only when the request needs domain-specific logic.

## Existing Validator Catalog To Leverage

```text
EQUAL
NOT_EQUAL
EMPTY
NOT_EMPTY
CONTAINS
CONTAINS_TEXT
STARTS_WITH
ENDS_WITH
MATCH_PATTERN
HAS_SIZE
GREATER_THAN
LESSER_THAN
IN_RANGE_OF
SORTED
CONTAINS_KEY
MATCH_SCHEMA
```

---

# 8.6 `/test-init`

## Goal

Generate or integrate a base automation project structure using Testara.

Support:

```text
- New empty automation project
- Existing Maven project
- Existing Spring Boot project
- Existing test project that needs Testara added
- API-only slice
- UI-only slice
- Mobile/Appium slice
- Database slice
- Streaming/Kafka slice
- Mixed full-stack slice
```

## Example Commands

```bash
testara-agent /test-init --type api --base-package com.company.automation
testara-agent /test-init --type ui --engine playwright
testara-agent /test-init --type fullstack --spring
testara-agent /test-init --integrate-existing .
```

## Behavior For Empty Project

Generate:

```text
pom.xml
src/test/resources/configuration.properties
src/test/resources/features
src/test/resources/files
src/test/resources/validations
src/test/java/{basePackage}/runner
src/test/java/{basePackage}/steps
```

## Behavior For Existing Project

Detect:

```text
- Maven/Gradle
- Java version
- Spring Boot or plain Java
- Existing Cucumber runner
- Existing feature root
- Existing test resources
- Existing Testara dependencies
- Existing package conventions
```

Then apply the minimal patch.

---

# 8.7 `/test-overview`

## Goal

Produce statistical overview of the test project.

## Example Commands

```bash
testara-agent /test-overview .
testara-agent /test-overview src/test/resources/features
```

## Output

```text
- Total feature files
- Total features
- Total scenarios
- Total scenario outlines
- Total examples rows
- Total steps
- Tags distribution
- Domain distribution
- API/UI/database/streaming guess
- Reused commands
- Reused validations
- Step definition coverage
- Missing step definitions
- Duplicate scenario candidates
- Average steps per scenario
- Longest scenarios
- Most used fixtures
- Most used request specs
- Most used validation files
```

## Example Summary

```text
Testara Project Overview

Feature files: 42
Scenarios: 318
Scenario outlines: 74
Examples rows: 1,240
Tags: @api 201, @ui 87, @smoke 22, @regression 211
Average steps/scenario: 6.4
Potential duplicates: 17
Missing step definitions: 9
High-complexity scenarios: 12
```

Export options:

```bash
testara-agent /test-overview . --format json
testara-agent /test-overview . --format markdown
testara-agent /test-overview . --format html
```

---

# 8.8 `/test-run`

## Goal

Accept natural-language user input, infer the best matching Cucumber tag filter, execute the matching Testara tests, and generate a clean concise report.

This skill is intentionally different from `/test-plan`: it does not generate tests. It selects and runs existing tests.

## Example Commands

```bash
testara-agent /test-run "run payment smoke tests"
testara-agent /test-run "run all api regression except slow tests"
testara-agent /test-run "run checkout tests on UI"
testara-agent /test-run "run failed payment scenarios only"
testara-agent /test-run "run @api and @payment but not @wip"
testara-agent /test-run --dry-run "run critical login tests"
```

## Inputs

Natural language input can include:

```text
- domain: payment, login, checkout, settlement
- layer: api, ui, database, streaming, elastic
- test type: smoke, regression, sanity, negative, e2e
- priority: P0, P1, critical
- exclusions: not slow, not wip, exclude flaky
- explicit tags: @api, @payment, @smoke
- execution mode: dry-run, run, rerun failed
```

## Core Behavior

```text
1. Index all feature files and tags.
2. Parse user intent.
3. Resolve matching tags.
4. Show selected tag expression and matching scenario count.
5. Build the Maven/Cucumber command.
6. Execute the test command only if allowed.
7. Parse Cucumber JSON/JUnit XML/report output.
8. Generate a concise report.
```

## Tag Filter Resolution

The skill should prefer deterministic tag matching before LLM inference.

Resolution priority:

```text
1. Explicit tags in user input
2. Known aliases from config
3. Exact tag match from indexed project tags
4. Domain match from feature path/title/scenario title
5. Semantic inference through LLM
6. Fallback: ask for clarification or show dry-run candidates
```

Example:

```text
User input:
run payment smoke tests

Resolved expression:
@payment and @smoke
```

Example:

```text
User input:
run all api regression except slow tests

Resolved expression:
@api and @regression and not @slow
```

Example:

```text
User input:
run critical checkout tests

Resolved expression:
@checkout and (@P0 or @critical)
```

## Suggested Tag Alias Config

Add optional config:

```properties
testara.agent.tag-alias.smoke=@smoke,@sanity
testara.agent.tag-alias.critical=@P0,@critical
testara.agent.tag-alias.api=@api
testara.agent.tag-alias.ui=@ui
testara.agent.tag-alias.flaky=@flaky
testara.agent.tag-alias.slow=@slow,@performance
```

## Run Planning Output

For `--dry-run`, output:

```text
Test Run Plan

Intent: run payment smoke tests
Resolved tag expression: @payment and @smoke
Matched features: 3
Matched scenarios: 12
Excluded scenarios: 0
Runner module: automation-tests
Command:
mvn test -Dcucumber.filter.tags="@payment and @smoke"
```

## Maven Command Strategy

Default command:

```bash
mvn test -Dcucumber.filter.tags="@payment and @smoke"
```

For module-specific projects:

```bash
mvn -pl automation-tests test -Dcucumber.filter.tags="@payment and @smoke"
```

For integration-test style execution:

```bash
mvn verify -Dcucumber.filter.tags="@payment and @smoke"
```

The agent should detect project convention from:

```text
- pom.xml
- surefire/failsafe config
- runner class
- existing README or scripts
- existing CI workflow
```

## Execution Guardrails

Before running:

```text
- Confirm project root is valid
- Confirm Maven wrapper or Maven executable exists
- Confirm tag expression matches at least one scenario
- Reject destructive shell operators
- Do not allow arbitrary command injection
- Restrict command to Maven/Testara execution template
- Support timeout
- Support max log size
```

Suggested config:

```properties
testara.agent.run.enabled=true
testara.agent.run.default-goal=test
testara.agent.run.timeout=15m
testara.agent.run.max-log-size=2MB
testara.agent.run.require-confirmation=false
testara.agent.run.report-json=target/cucumber.json
testara.agent.run.report-junit=target/surefire-reports
```

## Report Input Sources

The skill should parse, in order:

```text
1. Cucumber JSON report
2. JUnit XML report
3. Testara generated report metadata
4. Maven console output fallback
```

## Concise Report Format

```text
Test Run Report

Status: FAILED
Duration: 02m 14s
Tag filter: @payment and @smoke

Summary:
- Total: 12
- Passed: 10
- Failed: 2
- Skipped: 0

Failed Scenarios:
1. Refund approval - Approve pending refund
   Reason: Expected status 200 but got 409
   Feature: src/test/resources/features/api/payment/refund.feature

2. Payment capture - Capture authorized payment
   Reason: Timeout waiting for settlement event
   Feature: src/test/resources/features/api/payment/capture.feature

Suggested Next Actions:
- Re-run failed scenarios only.
- Check payment service test data state.
- Inspect settlement event consumer readiness.
```

## Machine-Readable Report

```json
{
  "status": "FAILED",
  "durationMs": 134000,
  "tagExpression": "@payment and @smoke",
  "total": 12,
  "passed": 10,
  "failed": 2,
  "skipped": 0,
  "failedScenarios": [
    {
      "feature": "src/test/resources/features/api/payment/refund.feature",
      "scenario": "Approve pending refund",
      "error": "Expected status 200 but got 409"
    }
  ],
  "suggestedNextActions": []
}
```

## Rerun Failed Support

Support:

```bash
testara-agent /test-run --rerun-failed
```

Possible implementation:

```text
- Read previous Cucumber rerun file if available
- Read previous report and collect failed scenario locations
- Generate rerun filter by file:line when supported
- Otherwise generate tag-based fallback if failed scenarios share tags
```

Example:

```bash
mvn test -Dcucumber.features="@target/rerun.txt"
```

Or:

```bash
mvn test -Dcucumber.filter.tags="@payment and @smoke"
```

## Report Output Options

```bash
testara-agent /test-run "run payment smoke tests" --report markdown
testara-agent /test-run "run payment smoke tests" --report json
testara-agent /test-run "run payment smoke tests" --report html
```

The default output should be concise Markdown in the console.

---

## 9. LLM Provider Abstraction

Avoid hardwiring to one provider.

```java
public interface LlmClient {
  LlmResponse complete(LlmRequest request);
}
```

Support:

```text
- OpenAI-compatible HTTP
- Ollama/local model
- GitHub Models later
- Disabled/mock provider for tests
```

Configuration:

```properties
testara.agent.enabled=true
testara.agent.provider=openai
testara.agent.model=gpt-4.1-mini
testara.agent.api-key=${TESTARA_AGENT_API_KEY}
testara.agent.temperature=0.2
testara.agent.max-context-files=80
testara.agent.max-output-files=20
testara.agent.apply-enabled=false
```

Security defaults:

```text
- API key only from environment variable
- Never log prompt payload by default
- Redact secrets from properties/files
- Do not send `.env`, credentials, tokens, private keys
```

---

## 10. Prompting Strategy

Use structured prompts, not freeform prompts.

Each skill should provide:

```text
System instruction
Repo profile
Relevant files
Known Testara commands
Known validators
Known step definitions
Known tags
User request
Output schema
Validation rules
```

Example for `/test-plan`:

```text
You are generating Testara-compatible Cucumber tests.
You must only use known step definitions unless you explicitly mark missing steps.
Use existing command syntax when dynamic data is needed.
Use existing validation tags when possible.
Return JSON matching GeneratedTestPlan schema.
```

Example for `/test-run`:

```text
You are resolving a natural-language test run request into a safe Cucumber tag expression.
Prefer explicit tags and indexed project tags.
Do not invent tags unless marked as suggestions.
Return JSON matching TestRunPlan schema.
```

---

## 11. File Generation Rules

## 11.1 Feature Files

Rules:

```text
- Must parse as valid Gherkin
- Must use stable names
- Must have tags
- Must avoid overusing Background
- Prefer Scenario Outline only when examples are meaningful
- Must keep one business behavior per feature
```

## 11.2 API Request Specs

Use Testara-style JSON request specifications:

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

## 11.3 UI Code

For UI generation, prefer Testara’s Screenplay-style API:

```java
Actor.withCurrentSession()
    .attemptsTo(
        Navigate.to("https://example.com"),
        Enter.text("admin").into("#username"),
        Click.on("#login-btn")
    );
```

---

## 12. Validation and Quality Gates

Every generated output should pass these gates.

## 12.1 Static Gates

```text
- Gherkin parse passes
- JSON parse passes
- Java source compiles
- No duplicate scenario names in same feature
- No empty Given/When/Then
- No generated secrets
- No unknown command unless generated
- No unknown validation unless generated
```

## 12.2 Testara-Specific Gates

```text
- Command classes implement CommandLogic<T>
- Command classes have @CommandTag
- Validator classes extend ValidatorLogic<ACTUAL, EXPECTED>
- Validator classes have @ValidationTag
- Custom command/validator package is in scan-locations
- Request spec path follows existing convention
```

## 12.3 Test Execution Gates

```text
- Tag expression is valid
- Tag expression matches at least one scenario
- Maven command is built from safe template
- Execution timeout is enforced
- Logs are capped
- Report output is parsed after execution
```

## 12.4 Build Gate

```bash
mvn -pl testara-agent,testara-agent-cli -am test
```

Optional project-level validation:

```bash
mvn test
```

---

## 13. Suggested Dependencies

For `testara-agent`:

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-core</artifactId>
</dependency>
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-command</artifactId>
</dependency>
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-validation</artifactId>
</dependency>
<dependency>
  <groupId>io.cucumber</groupId>
  <artifactId>gherkin</artifactId>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
```

For `testara-agent-cli`:

```xml
<dependency>
  <groupId>info.picocli</groupId>
  <artifactId>picocli</artifactId>
</dependency>
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-agent</artifactId>
</dependency>
```

Keep Spring AI optional. Since Testara is a library/framework, the safer baseline is a small provider abstraction rather than forcing Spring into the agent module.

---

## 14. Configuration Proposal

```properties
# General
testara.agent.enabled=true
testara.agent.provider=openai
testara.agent.model=gpt-4.1-mini
testara.agent.api-key=${TESTARA_AGENT_API_KEY}
testara.agent.temperature=0.2
testara.agent.max-context-files=80
testara.agent.max-output-files=20
testara.agent.apply-enabled=false

# Run skill
testara.agent.run.enabled=true
testara.agent.run.default-goal=test
testara.agent.run.timeout=15m
testara.agent.run.max-log-size=2MB
testara.agent.run.require-confirmation=false
testara.agent.run.report-json=target/cucumber.json
testara.agent.run.report-junit=target/surefire-reports

# Tag aliases
testara.agent.tag-alias.smoke=@smoke,@sanity
testara.agent.tag-alias.critical=@P0,@critical
testara.agent.tag-alias.api=@api
testara.agent.tag-alias.ui=@ui
testara.agent.tag-alias.flaky=@flaky
testara.agent.tag-alias.slow=@slow,@performance
```

---

## 15. CLI Syntax Proposal

```bash
testara-agent /test-summary <path>
testara-agent /test-review <path> [--format markdown|json]
testara-agent /test-plan "<user input>" [--interactive] [--slice api|ui|database|streaming|fullstack] [--patch] [--apply]
testara-agent /test-command "<description>" [--package com.company.commands] [--patch]
testara-agent /test-validation "<description>" [--mode json|java|auto] [--patch]
testara-agent /test-init [--type api|ui|database|streaming|fullstack] [--engine selenium|playwright|appium] [--base-package ...] [--patch]
testara-agent /test-overview <path> [--format markdown|json|html]
testara-agent /test-run "<user input>" [--dry-run] [--report markdown|json|html] [--rerun-failed]
```

---

## 16. Important Design Decisions

## 16.1 Use LLM As Reasoning Layer, Not Source Of Truth

The source of truth must be:

```text
- Parsed feature files
- Existing step definitions
- Existing commands
- Existing validations
- Existing project config
- Existing tags
- Existing reports
```

The LLM should only help with:

```text
- Summarization
- Semantic grouping
- Test idea generation
- Review explanation
- Code drafting
- Natural language to tag expression mapping
- Report explanation
```

## 16.2 Default To Patch, Not Write

Agent output should first be a proposed patch.

```bash
testara-agent /test-plan "refund flow" --patch
```

Only write files with:

```bash
--apply
```

## 16.3 Default `/test-run` To Safe Commands

The run skill must never execute arbitrary shell text. It should generate commands from fixed templates only.

Allowed command templates:

```bash
mvn test -Dcucumber.filter.tags="{tagExpression}"
mvn verify -Dcucumber.filter.tags="{tagExpression}"
mvn -pl {module} test -Dcucumber.filter.tags="{tagExpression}"
mvn -pl {module} verify -Dcucumber.filter.tags="{tagExpression}"
```

## 16.4 Keep Generated Files Traceable

For feature files:

```gherkin
# Generated by Testara Agent.
# Source request: refund approval flow
# Review before committing.
```

For Java:

```java
/**
 * Generated by Testara Agent.
 * Review before committing.
 */
```

## 16.5 Do Not Leak Secrets

Before sending context to LLM, redact:

```text
password
secret
token
authorization
api-key
client-secret
private-key
cookie
session
```

---

## 17. Suggested Implementation Phases

## Phase 1 — Foundation

Deliver:

```text
- Add testara-agent module
- Add testara-agent-cli module
- Add AgentSkill interface
- Add ProjectIndexer
- Add FeatureParser
- Add TestaraProjectProfile
- Add read-only /test-summary
- Add read-only /test-overview
```

Acceptance criteria:

```text
- Can summarize a single feature
- Can summarize a feature directory
- Can count features/scenarios/tags
- No LLM required yet
```

---

## Phase 2 — Review Skill

Deliver:

```text
- /test-review
- Duplicate scenario detection
- Weak assertion detection
- Scenario complexity scoring
- Tag/priority recommendation
- Markdown and JSON reports
```

Acceptance criteria:

```text
- Finds exact duplicate scenario names
- Finds similar step sequences
- Flags scenarios with no meaningful Then
- Suggests Background or Scenario Outline extraction
- Produces priority recommendation
```

---

## Phase 3 — Command and Validation Generation

Deliver:

```text
- /test-command
- /test-validation
- Command Java generator
- Validator Java generator
- Validation JSON generator
- Unit test generator
```

Acceptance criteria:

```text
- Generated command compiles
- Generated validator compiles
- Generated validation JSON is valid
- Suggested scan-location config is correct
```

---

## Phase 4 — Test Run Skill

Deliver:

```text
- /test-run
- Tag index
- Natural-language to tag expression resolver
- Dry-run mode
- Safe Maven command builder
- Test execution wrapper
- Cucumber JSON parser
- JUnit XML parser
- Concise Markdown report
- Machine-readable JSON report
```

Acceptance criteria:

```text
- Resolves explicit tags directly
- Resolves common aliases such as smoke, regression, api, ui, critical
- Shows matched scenario count before execution
- Can run Maven with safe tag filter
- Produces clean concise report
- Can parse failed scenarios and error messages
- Supports rerun failed where report data is available
```

---

## Phase 5 — Test Plan Generation

Deliver:

```text
- /test-plan
- Interactive question model
- Feature generation
- Request spec generation
- Validation file generation
- Missing-step report
- Patch proposal mode
```

Acceptance criteria:

```text
- Does not hallucinate existing steps
- Marks missing steps clearly
- Generates Testara-compatible `.feature`
- Places files in correct directory
- Can generate API and UI slices
```

---

## Phase 6 — Init/Integration Skill

Deliver:

```text
- /test-init
- Empty project bootstrap
- Existing Maven project integration
- API/UI/database/streaming slice selection
- BOM/dependency patch generation
- configuration.properties generation
```

Acceptance criteria:

```text
- Can generate API-only Testara project
- Can generate UI Selenium/Playwright/Appium project
- Can integrate Testara into existing Maven project without overwriting existing files
- Can detect existing Testara dependencies
```

---

## Phase 7 — MCP or IDE Integration

Deliver later:

```text
- testara-agent-mcp
- Expose skills as MCP tools
- IDE/chat integration
- Git patch preview
- Repo-aware interactive planning
```

---

## 18. Recommended First PR Sequence

```text
PR 1: Add testara-agent and testara-agent-cli skeleton
PR 2: Add project/feature/step/command/validation/tag indexers and JSONL Project Knowledge Store
PR 3: Add /test-summary and /test-overview
PR 4: Add /test-review
PR 5: Add /test-command and /test-validation
PR 6: Add /test-run
PR 7: Add /test-plan interactive planner
PR 8: Add /test-init bootstrap/integration
PR 9: Add MCP/IDE integration
```

Recommended implementation order:

```text
index first
→ summarize/overview
→ review
→ generate commands/validators
→ run by tags
→ generate feature plans
→ bootstrap projects
```

---


---

---

## 21. Project Knowledge Store, JSONL Cache, and Fast Local Query Plan

This section adds a persistent knowledge layer so Testara Agent does not repeatedly reprocess the whole project when there is no meaningful diff.

The core architecture should become:

```text
Skill
  → ProjectKnowledgeService
    → load cached JSONL snapshot if fresh
    → partial re-index changed files if small diff
    → full re-index if structural/build/config diff
    → KnowledgeQueryService
```

Skills should not call `ProjectIndexer` directly. They should always use the knowledge service:

```java
ProjectKnowledgeSnapshot snapshot =
    context.knowledgeService().loadOrIndex(projectRoot);
```

### 21.1 Goals

```text
- Fast repeated agent calls
- Efficient local project cache
- Incremental re-indexing
- Human-debuggable files
- No mandatory database dependency
- Optional fast local query backend later
- Optional versioned SQL backend later
```

Default MVP:

```text
JSONL cache under .testara-agent/knowledge
```

Optional later:

```text
H2 / SQLite / Dolt query backend
```

---

### 21.2 New Package Structure

Add:

```text
io.github.ygrip.testara.agent.knowledge
├── ProjectKnowledgeService.java
├── ProjectKnowledgeStore.java
├── JsonlProjectKnowledgeStore.java
├── ProjectKnowledgeSnapshot.java
├── ProjectFingerprint.java
├── FileFingerprint.java
├── FingerprintService.java
├── FingerprintDiff.java
├── KnowledgeInvalidationService.java
├── IncrementalIndexer.java
├── KnowledgeQueryService.java
├── JsonlKnowledgeQueryService.java
└── KnowledgeCacheConfig.java
```

Optional later:

```text
io.github.ygrip.testara.agent.knowledge.sql
├── SqlKnowledgeStore.java
├── SqlKnowledgeQueryService.java
├── H2KnowledgeStore.java
├── SqliteKnowledgeStore.java
├── DoltKnowledgeStore.java
└── KnowledgeSchemaMigrator.java
```

---

### 21.3 Storage Location

Default project-local cache:

```text
.testara-agent/
├── knowledge/
│   ├── manifest.json
│   ├── file-fingerprints.jsonl
│   ├── features.jsonl
│   ├── scenarios.jsonl
│   ├── steps.jsonl
│   ├── step-definitions.jsonl
│   ├── tags.jsonl
│   ├── commands.jsonl
│   ├── validations.jsonl
│   ├── request-specs.jsonl
│   ├── validation-files.jsonl
│   ├── project-conventions.jsonl
│   └── run-history.jsonl
└── reports/
```

Recommended `.gitignore`:

```gitignore
.testara-agent/
target/testara-agent/
```

Configuration:

```properties
testara.agent.knowledge.enabled=true
testara.agent.knowledge.storage=jsonl
testara.agent.knowledge.cache-dir=.testara-agent/knowledge
testara.agent.knowledge.fingerprint-mode=HYBRID
```

---

### 21.4 Why JSONL?

JSONL should be the default because it is:

```text
- simple
- append-friendly
- stream-friendly
- easy to debug
- easy to partially rewrite by entity type
- safe for large projects
- easy to import into SQL/vector stores later
```

Each line is one independent JSON object.

Example:

```json
{"type":"scenario","id":"scenario:payment/refund.feature:12","featureId":"feature:payment/refund.feature","name":"Approve pending refund request","tags":["@api","@payment","@P1"],"line":12}
{"type":"scenario","id":"scenario:payment/refund.feature:21","featureId":"feature:payment/refund.feature","name":"Reject already approved refund request","tags":["@api","@payment","@negative"],"line":21}
```

---

### 21.5 Manifest File

`manifest.json` stores global snapshot metadata.

```json
{
  "schemaVersion": 1,
  "agentVersion": "1.0.0",
  "projectRoot": "/workspace/my-automation",
  "createdAt": "2026-05-30T12:00:00Z",
  "updatedAt": "2026-05-30T12:05:00Z",
  "buildTool": "MAVEN",
  "javaVersion": "21",
  "testaraVersion": "1.1.4",
  "storage": "jsonl",
  "fingerprintMode": "HYBRID",
  "projectHash": "sha256:...",
  "files": {
    "fingerprints": "file-fingerprints.jsonl",
    "features": "features.jsonl",
    "scenarios": "scenarios.jsonl",
    "steps": "steps.jsonl",
    "stepDefinitions": "step-definitions.jsonl",
    "tags": "tags.jsonl",
    "commands": "commands.jsonl",
    "validations": "validations.jsonl",
    "requestSpecs": "request-specs.jsonl",
    "validationFiles": "validation-files.jsonl"
  }
}
```

---

### 21.6 JSONL Entity Files

#### `file-fingerprints.jsonl`

```json
{"path":"pom.xml","type":"BUILD","size":12039,"lastModifiedMillis":1770000000000,"sha256":"abc"}
{"path":"src/test/resources/features/payment/refund.feature","type":"FEATURE","size":2100,"lastModifiedMillis":1770000100000,"sha256":"def"}
{"path":"src/test/java/com/company/steps/PaymentSteps.java","type":"STEP_DEFINITION","size":4200,"lastModifiedMillis":1770000200000,"sha256":"ghi"}
```

#### `features.jsonl`

```json
{"id":"feature:src/test/resources/features/payment/refund.feature","path":"src/test/resources/features/payment/refund.feature","name":"Refund approval","tags":["@api","@payment","@regression"],"line":1}
```

#### `scenarios.jsonl`

```json
{"id":"scenario:src/test/resources/features/payment/refund.feature:12","featureId":"feature:src/test/resources/features/payment/refund.feature","path":"src/test/resources/features/payment/refund.feature","name":"Approve pending refund request","type":"SCENARIO","tags":["@P1","@positive"],"line":12,"stepCount":3}
```

#### `steps.jsonl`

```json
{"id":"step:src/test/resources/features/payment/refund.feature:13","scenarioId":"scenario:src/test/resources/features/payment/refund.feature:12","keyword":"Given","text":"a refund request exists with id uuid()","line":13,"commandsUsed":["uuid"],"matchedStepDefinitionId":"stepdef:PaymentSteps:refundRequestExists"}
```

#### `step-definitions.jsonl`

```json
{"id":"stepdef:PaymentSteps:refundRequestExists","annotation":"Given","expression":"a refund request exists with id {string}","className":"PaymentSteps","methodName":"refundRequestExists","path":"src/test/java/com/company/steps/PaymentSteps.java","line":24}
```

#### `tags.jsonl`

```json
{"tag":"@api","featureCount":12,"scenarioCount":104,"paths":["src/test/resources/features/payment/refund.feature"]}
{"tag":"@payment","featureCount":3,"scenarioCount":28,"paths":["src/test/resources/features/payment/refund.feature"]}
{"tag":"@smoke","featureCount":5,"scenarioCount":18,"paths":["src/test/resources/features/login/login.feature"]}
```

#### `commands.jsonl`

```json
{"command":"uuid","aliases":[],"returnType":"String","cacheable":false,"source":"BUILT_IN","className":"UuidCommand"}
{"command":"customer_code","aliases":["customer code"],"returnType":"String","cacheable":false,"source":"CUSTOM","className":"CustomerCodeCommand","path":"src/test/java/com/company/commands/CustomerCodeCommand.java"}
```

#### `validations.jsonl`

```json
{"validation":"EQUAL","aliases":[],"actualType":"Object","expectedType":"Object","source":"BUILT_IN"}
{"validation":"PALINDROME","aliases":["is palindrome"],"actualType":"String","expectedType":"Boolean","source":"CUSTOM","className":"PalindromeValidation","path":"src/test/java/com/company/validations/PalindromeValidation.java"}
```

#### `request-specs.jsonl`

```json
{"id":"request:payment/create-refund","path":"src/test/resources/files/payment/create-refund.json","specification":"payment-api","method":"POST","url":"/refunds","contentType":"application/json"}
```

#### `validation-files.jsonl`

```json
{"id":"validation-file:payment/refund-approved","path":"src/test/resources/validations/payment/refund-approved.json","validations":["EQUAL","CONTAINS_TEXT"]}
```

#### `run-history.jsonl`

```json
{"runId":"run:20260530-120000","startedAt":"2026-05-30T12:00:00Z","tagExpression":"@payment and @smoke","status":"FAILED","total":12,"passed":10,"failed":2,"durationMs":134000}
```

---

### 21.7 Fingerprint Strategy

For each tracked file, store:

```text
relative path
file type
file size
last modified time
sha256 hash
```

Fingerprint modes:

```text
FAST    = path + size + lastModifiedMillis
SAFE    = sha256 all tracked files
HYBRID  = fast check first, hash only suspected changed files
```

Recommended default:

```text
HYBRID
```

Fast path:

```text
same path + same size + same lastModifiedMillis = unchanged
```

Safe path:

```text
same sha256 = unchanged
```

---

### 21.8 Invalidation Rules

Partial feature re-index when:

```text
src/test/resources/features/**/*.feature changed
```

Update:

```text
features.jsonl
scenarios.jsonl
steps.jsonl
tags.jsonl
overview stats
```

Partial Java step re-index when:

```text
src/test/java/**/*.java changed
and file contains @Given, @When, @Then, @And, or @But
```

Update:

```text
step-definitions.jsonl
steps.jsonl match status
missing step coverage
```

Partial command re-index when Java files contain:

```text
@CommandTag
CommandLogic
```

Partial validation re-index when Java files contain:

```text
@ValidationTag
ValidatorLogic
```

Partial request spec re-index when:

```text
src/test/resources/files/**/*.json changed
```

Full re-index when:

```text
pom.xml changed
build.gradle changed
configuration.properties changed
testara-agent.yaml changed
module added/deleted
feature root changed
dependency version changed
Java package root changed
scan-location config changed
```

---

### 21.9 Java Interfaces

#### `ProjectKnowledgeService`

```java
public interface ProjectKnowledgeService {

  ProjectKnowledgeSnapshot loadOrIndex(Path projectRoot);

  ProjectKnowledgeSnapshot refresh(Path projectRoot);

  KnowledgeStatus status(Path projectRoot);

  void clear(Path projectRoot);
}
```

#### `ProjectKnowledgeStore`

```java
public interface ProjectKnowledgeStore {

  Optional<ProjectKnowledgeSnapshot> load(Path projectRoot);

  void save(Path projectRoot, ProjectKnowledgeSnapshot snapshot);

  void update(KnowledgeUpdate update);

  void clear(Path projectRoot);
}
```

#### `ProjectKnowledgeSnapshot`

```java
public record ProjectKnowledgeSnapshot(
    int schemaVersion,
    Instant createdAt,
    Instant updatedAt,
    ProjectFingerprint fingerprint,
    TestaraProjectProfile profile,
    KnowledgeStats stats
) {}
```

#### `FileFingerprint`

```java
public record FileFingerprint(
    Path path,
    FileType type,
    long size,
    long lastModifiedMillis,
    String sha256
) {}
```

---

### 21.10 Incremental Indexer

```java
public final class IncrementalIndexer {

  public ProjectKnowledgeSnapshot index(Path projectRoot) {
    var previous = knowledgeStore.load(projectRoot);
    var currentFingerprint = fingerprintService.scan(projectRoot);

    if (previous.isPresent()
        && previous.get().fingerprint().equals(currentFingerprint)) {
      return previous.get();
    }

    var diff = fingerprintDiff.diff(
        previous.map(ProjectKnowledgeSnapshot::fingerprint).orElse(null),
        currentFingerprint
    );

    if (diff.requiresFullReindex()) {
      return fullReindex(projectRoot, currentFingerprint);
    }

    return partialReindex(projectRoot, previous.orElse(null), diff, currentFingerprint);
  }
}
```

---

### 21.11 Knowledge Query Service

Skills should not manually read JSONL files.

```java
public interface KnowledgeQueryService {

  List<FeatureRecord> findFeatures(KnowledgeQuery query);

  List<ScenarioRecord> findScenarios(KnowledgeQuery query);

  List<StepDefinitionRecord> findStepDefinitions(KnowledgeQuery query);

  List<TagRecord> findTags(KnowledgeQuery query);

  List<CommandRecord> findCommands(KnowledgeQuery query);

  List<ValidationRecord> findValidations(KnowledgeQuery query);

  ProjectOverviewStats overview();
}
```

Example usage in `/test-run`:

```java
var tags = knowledgeQueryService.findTags(
    KnowledgeQuery.builder()
        .text("payment smoke")
        .build()
);

var scenarios = knowledgeQueryService.findScenarios(
    KnowledgeQuery.builder()
        .tagExpression("@payment and @smoke")
        .build()
);
```

---

### 21.12 Knowledge CLI Commands

Add:

```bash
testara-agent knowledge status
testara-agent knowledge refresh
testara-agent knowledge clear
testara-agent knowledge inspect tags
testara-agent knowledge inspect scenarios
testara-agent knowledge compact
```

Example output:

```text
Testara Knowledge Status

Cache: .testara-agent/knowledge
Storage: JSONL
Fingerprint mode: HYBRID
Last indexed: 2026-05-30 12:05:00
Tracked files: 214
Features: 42
Scenarios: 318
Step definitions: 126
Commands: 54
Validations: 43
Status: fresh
```

---

### 21.13 JSONL Compaction

Support:

```bash
testara-agent knowledge compact
```

Compaction behavior:

```text
- remove stale records
- keep latest record per ID
- rewrite sorted JSONL files
- rebuild manifest
- recalculate project hash
```

Config:

```properties
testara.agent.knowledge.compact-after-updates=100
```

For MVP, simpler behavior is acceptable:

```text
rewrite affected JSONL files per partial re-index
```

---

### 21.14 Optional Fast Query Backend

JSONL is the default. For very large projects, add optional query backends.

Supported strategy:

```text
JSONL is the canonical local cache.
The query backend is a derived index.
If the query backend is missing or stale, rebuild it from JSONL.
```

Candidate backends:

| Backend | Fit | Recommendation |
|---|---|---|
| JSONL only | Simple, portable, debug-friendly | Default MVP |
| H2 | Pure Java embedded SQL | Best pure-Java query backend |
| SQLite | Fast local SQL, single file | Best pragmatic local query backend |
| DuckDB | Fast analytics | Good for heavy reporting, native dependency |
| Dolt | Versioned SQL database, MySQL-compatible | Optional advanced mode |
| Vector DB | Semantic search | Later only |

---

### 21.15 Can We Use Dolt?

Yes, but it should be optional rather than the default.

Dolt is a version-controlled SQL database that can be cloned, branched, merged, pushed, and pulled like Git while being queried like a MySQL database. It is useful for versioned data and branch-aware knowledge history.

Good fit for Dolt:

```text
- SQL querying over project knowledge
- versioned knowledge snapshots
- branch-aware cache
- diffing test knowledge between Git branches
- sharing project knowledge snapshots across team members
- historical test structure analysis
- comparing scenario coverage before/after a PR
```

Where Dolt is too heavy for MVP:

```text
- It adds a database binary/server dependency.
- It is not as simple as JSONL for local cache.
- Java integration normally goes through MySQL-compatible JDBC.
- It complicates installation for Claude, Cursor, Copilot, and Codex users.
- Most agent skills only need quick local lookups, not database versioning.
```

Recommended Dolt position:

```text
Default:
  JSONL

Fast local SQL:
  H2 or SQLite

Advanced versioned knowledge:
  Dolt
```

Optional Dolt config:

```properties
testara.agent.knowledge.storage=jsonl
testara.agent.knowledge.query-backend=dolt
testara.agent.knowledge.dolt.database=.testara-agent/knowledge-dolt
testara.agent.knowledge.dolt.server.enabled=false
testara.agent.knowledge.dolt.auto-commit=true
```

Hybrid lifecycle:

```text
1. JSONL remains canonical local cache.
2. Dolt imports JSONL-derived records into SQL tables.
3. Dolt commits snapshots after indexing.
4. Agent queries Dolt for SQL-heavy or history-aware operations.
5. If Dolt is unavailable, fallback to JSONL query service.
```

Dolt schema example:

```sql
CREATE TABLE file_fingerprints (
  path VARCHAR(1024) PRIMARY KEY,
  type VARCHAR(64),
  size BIGINT,
  last_modified_millis BIGINT,
  sha256 VARCHAR(128)
);

CREATE TABLE features (
  id VARCHAR(512) PRIMARY KEY,
  path VARCHAR(1024),
  name TEXT,
  line INT
);

CREATE TABLE scenarios (
  id VARCHAR(512) PRIMARY KEY,
  feature_id VARCHAR(512),
  path VARCHAR(1024),
  name TEXT,
  type VARCHAR(64),
  line INT,
  step_count INT
);

CREATE TABLE scenario_tags (
  scenario_id VARCHAR(512),
  tag VARCHAR(128),
  PRIMARY KEY (scenario_id, tag)
);

CREATE TABLE step_definitions (
  id VARCHAR(512) PRIMARY KEY,
  annotation VARCHAR(32),
  expression TEXT,
  class_name VARCHAR(512),
  method_name VARCHAR(512),
  path VARCHAR(1024),
  line INT
);
```

After successful indexing, Dolt can commit the snapshot:

```sql
CALL DOLT_ADD('-A');
CALL DOLT_COMMIT('-m', 'Update Testara project knowledge snapshot');
```

Commit metadata should include:

```text
- project hash
- Git commit hash if available
- indexed file count
- scenario count
- agent version
```

Branch strategy:

```text
Git branch: feature/refund-tests
Dolt branch: feature_refund_tests
```

---

### 21.16 H2 / SQLite Alternative

If fast local query is needed before Dolt, use H2 or SQLite.

#### H2

Best when pure Java matters.

Pros:

```text
- pure Java
- embedded
- simple setup for Java projects
- no external binary
```

Cons:

```text
- less useful for branch-aware versioning
```

#### SQLite

Best pragmatic local SQL option.

Pros:

```text
- very fast local queries
- mature
- single file
- great for local cache
```

Cons:

```text
- JDBC driver includes native components
- not pure Java in the strictest sense
```

Recommendation:

```text
MVP:
  JSONL only

Next:
  H2 if pure Java is mandatory
  SQLite if performance and simplicity are preferred

Advanced:
  Dolt for branch-aware versioned knowledge
```

---

### 21.17 Recommended MVP Implementation

Deliver:

```text
- ProjectKnowledgeService
- JsonlProjectKnowledgeStore
- FingerprintService
- IncrementalIndexer
- KnowledgeQueryService
- JSONL files under .testara-agent/knowledge
- knowledge status/refresh/clear/compact commands
```

Acceptance criteria:

```text
- First run builds project knowledge.
- Second run with no diff reuses cache.
- Changed feature file only re-indexes feature-related records.
- Changed step file only re-indexes step definitions and step match status.
- Build/config change triggers full re-index.
- /test-overview reads from knowledge store.
- /test-summary reads from knowledge store.
- /test-run resolves tags from knowledge store.
```

---

### 21.18 Future Implementation

Phase 2:

```text
- Add H2 or SQLite query backend.
- Keep JSONL as canonical fallback.
- Rebuild SQL tables from JSONL when stale.
```

Phase 3:

```text
- Add Dolt backend.
- Use Dolt for branch-aware knowledge history.
- Add PR/branch comparison reports.
```

Future command:

```bash
testara-agent knowledge diff main..feature/refund-tests
```

Example output:

```text
Knowledge Diff

Added scenarios: 12
Removed scenarios: 2
Changed scenarios: 7
New tags: @refund, @settlement
Removed tags: @legacy-payment
Step definitions added: 4
Potential duplicate scenarios added: 3
```

---

### 21.19 Final Recommendation For Knowledge Store

Use this approach:

```text
Default:
  JSONL knowledge cache

Fast local query:
  JSONL query service first
  optional H2/SQLite later

Versioned data / branch diff:
  optional Dolt backend later
```

Dolt is technically viable, but it should not be the default for a lightweight developer assistant. It is best positioned as an advanced backend for branch-aware project knowledge, team-shared knowledge snapshots, and test coverage diffs across branches.

## 20. Installation and AI Assistant Integration Guide

This section defines how users should install Testara Agent and integrate it with Claude, Cursor, Codex-compatible agents, GitHub Copilot, and direct CLI workflows.

The recommended distribution model is layered:

```text
1. CLI binary / executable JAR
2. MCP server mode
3. Optional Docker image
4. Optional Maven plugin
5. Optional IDE-specific wrapper later
```

The main principle is:

```text
CLI for universal local usage
MCP for AI assistant integration
Docker for isolated/CI execution
Maven plugin for Java-native project workflows
```

---

## 20.1 Distribution Artifacts

Publish these artifacts:

```text
testara-agent-cli.jar
testara-agent native binary
testara-agent-mcp.jar
testara-agent-bom
testara-agent-maven-plugin       # optional later
ghcr.io/ygrip/testara-agent      # optional Docker image
```

Recommended first release:

```text
testara-agent-cli.jar
testara-agent executable script
```

Recommended later release:

```text
native binaries:
- testara-agent-linux-amd64
- testara-agent-linux-arm64
- testara-agent-darwin-amd64
- testara-agent-darwin-arm64
- testara-agent-windows-amd64.exe
```

---

## 20.2 CLI Installation

### Option A — Native Binary

Best user experience.

```bash
curl -L https://github.com/ygrip/testara/releases/latest/download/testara-agent \
  -o ~/.local/bin/testara-agent

chmod +x ~/.local/bin/testara-agent
```

Verify:

```bash
testara-agent --version
testara-agent /test-overview .
```

### Option B — Executable JAR

Simplest first release.

```bash
mkdir -p ~/.testara
curl -L https://github.com/ygrip/testara/releases/latest/download/testara-agent-cli.jar \
  -o ~/.testara/testara-agent-cli.jar
```

Run:

```bash
java -jar ~/.testara/testara-agent-cli.jar /test-overview .
```

Optional shell alias:

```bash
alias testara-agent='java -jar ~/.testara/testara-agent-cli.jar'
```

### Option C — Maven Plugin

Optional later.

```bash
./mvnw io.github.ygrip:testara-agent-maven-plugin:run \
  -Dtestara.agent.command="/test-overview ."
```

Example:

```bash
./mvnw io.github.ygrip:testara-agent-maven-plugin:run \
  -Dtestara.agent.command="/test-review src/test/resources/features"
```

---

## 20.3 CLI Usage

Direct local usage:

```bash
testara-agent /test-summary src/test/resources/features/login.feature
testara-agent /test-review src/test/resources/features
testara-agent /test-plan "Create tests for refund approval flow"
testara-agent /test-command "generate customer id with prefix and timestamp"
testara-agent /test-validation "validate response contains active users sorted by createdDate desc"
testara-agent /test-init --type api --base-package com.company.automation
testara-agent /test-overview .
testara-agent /test-run --dry-run "run payment smoke tests"
```

Actual test execution:

```bash
testara-agent /test-run "run payment smoke tests" --execute
```

or:

```bash
TESTARA_AGENT_RUN_ENABLED=true testara-agent /test-run "run payment smoke tests"
```

Recommended default:

```text
/test-run without explicit execution flag should first show the resolved tag filter and matched scenario count.
```

---

## 20.4 MCP Server Mode

Testara Agent should expose an MCP server through stdio:

```bash
testara-agent mcp
```

or with JAR:

```bash
java -jar ~/.testara/testara-agent-cli.jar mcp
```

MCP tools to expose:

```text
testara_summary
testara_review
testara_plan
testara_command
testara_validation
testara_init
testara_overview
testara_run
```

MCP prompts to expose:

```text
test-summary
test-review
test-plan
test-command
test-validation
test-init
test-overview
test-run
```

Tool naming should avoid slash prefixes because MCP tool names are usually plain identifiers. Slash commands can be exposed as MCP prompts or documented assistant instructions.

### Example MCP Tool Contract: `testara_run`

```json
{
  "name": "testara_run",
  "description": "Resolve natural language test intent into safe Cucumber tag filters, run matching Testara tests, and return a concise report.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "projectRoot": {
        "type": "string"
      },
      "input": {
        "type": "string"
      },
      "dryRun": {
        "type": "boolean",
        "default": true
      },
      "allowExecution": {
        "type": "boolean",
        "default": false
      },
      "reportFormat": {
        "type": "string",
        "enum": ["markdown", "json"],
        "default": "markdown"
      }
    },
    "required": ["input"]
  }
}
```

Default for MCP:

```text
dryRun=true
allowExecution=false
```

Actual execution must require either explicit user approval or configuration.

---

## 20.5 Claude Code Integration

### Project-Scoped Installation

From the automation project root:

```bash
claude mcp add --transport stdio --scope project testara \
  -- testara-agent mcp
```

This should create or update project MCP configuration.

Equivalent `.mcp.json`:

```json
{
  "mcpServers": {
    "testara": {
      "command": "testara-agent",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "false",
        "TESTARA_AGENT_WRITE_ENABLED": "false"
      },
      "timeout": 600000
    }
  }
}
```

### User-Scoped Installation

```bash
claude mcp add --transport stdio --scope user testara \
  -- testara-agent mcp
```

### Example Prompts In Claude

```text
Use Testara to summarize this repo's test structure.
```

```text
Use Testara to review src/test/resources/features/payment and find duplicated scenarios.
```

```text
Use Testara to dry-run payment smoke tests and show the matching tag expression.
```

```text
Use Testara to generate a test plan for refund approval API regression.
```

### Recommended Claude Defaults

```text
- Enable read-only tools by default.
- Keep file writing disabled unless explicitly approved.
- Keep /test-run in dry-run mode by default.
- Ask approval before executing Maven commands.
```

---

## 20.6 Cursor Integration

Cursor should use the same MCP server.

Recommended configuration:

```json
{
  "mcpServers": {
    "testara": {
      "command": "testara-agent",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "false",
        "TESTARA_AGENT_WRITE_ENABLED": "false"
      }
    }
  }
}
```

Generic setup flow:

```text
Cursor
→ Settings
→ MCP
→ Add Server
→ Name: testara
→ Command: testara-agent
→ Args: mcp
```

Example prompts in Cursor Agent:

```text
Use the Testara overview tool for this project.
```

```text
Use Testara to review duplicated scenarios in payment features.
```

```text
Use Testara to create a feature plan for checkout cancellation.
```

```text
Use Testara to dry-run "all api regression except slow tests".
```

Recommended Cursor defaults:

```text
- Dry-run for test execution.
- Patch preview for generated files.
- No automatic apply unless user confirms.
```

---

## 20.7 Codex-Compatible Agent Integration

Codex-style environments should support two paths.

### Path A — MCP, Where Supported

If the Codex client supports MCP, register:

```json
{
  "mcpServers": {
    "testara": {
      "command": "testara-agent",
      "args": ["mcp"]
    }
  }
}
```

TOML-style equivalent if required by the client:

```toml
[mcp_servers.testara]
command = "testara-agent"
args = ["mcp"]
```

### Path B — CLI Fallback

Every coding agent with shell access can use the CLI:

```bash
testara-agent /test-overview .
testara-agent /test-review src/test/resources/features
testara-agent /test-run --dry-run "run payment smoke tests"
```

For Codex-compatible setups, document both:

```text
Use MCP if the client supports it.
Use CLI if the client has shell access but no MCP support.
```

Recommended default:

```text
Codex-style agents should call /test-run with --dry-run first, then request explicit approval before execution.
```

---

## 20.8 GitHub Copilot Chat Integration

For Copilot Chat in VS Code, add a repo-level `.vscode/mcp.json`.

```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "testara-agent",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "false",
        "TESTARA_AGENT_WRITE_ENABLED": "false"
      }
    }
  }
}
```

User-level VS Code settings can also define the server for personal use.

Example prompts in Copilot Chat Agent mode:

```text
Use Testara to review duplicated scenarios in the payment features.
```

```text
Use Testara to generate a concise test overview report.
```

```text
Use Testara to dry-run "api regression except slow tests".
```

If MCP prompts are exposed, users can invoke assistant-facing prompts such as:

```text
/mcp.testara.test-summary
/mcp.testara.test-review
/mcp.testara.test-run
```

Recommended Copilot defaults:

```text
- Read-only tools enabled.
- Write/apply tools disabled by default.
- /test-run dry-run only by default.
- Actual execution requires explicit user approval.
```

---

## 20.9 Docker Installation

Useful for CI, sandboxing, and reproducible local execution.

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  ghcr.io/ygrip/testara-agent:latest \
  /test-overview .
```

Docker MCP configuration example:

```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-v",
        "${workspaceFolder}:/workspace",
        "-w",
        "/workspace",
        "ghcr.io/ygrip/testara-agent:latest",
        "mcp"
      ],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "false"
      }
    }
  }
}
```

Pros:

```text
- Isolated
- Reproducible
- Good for CI
```

Cons:

```text
- Slower startup
- Volume mapping complexity
- Requires Docker
```

---

## 20.10 Project-Level Agent Configuration

Add optional config:

```text
testara-agent.yaml
```

Example:

```yaml
project:
  featureRoots:
    - src/test/resources/features
  requestSpecRoots:
    - src/test/resources/files
  validationRoots:
    - src/test/resources/validations

run:
  enabled: false
  requireConfirmation: true
  defaultGoal: test
  timeout: 15m
  reportJson:
    - target/cucumber.json
    - target/cucumber-reports/cucumber.json
  reportJunit:
    - target/surefire-reports
    - target/failsafe-reports

write:
  enabled: false
  requireConfirmation: true

llm:
  provider: openai
  model: gpt-4.1-mini
  sendSourceCode: false
  redactSecrets: true

tagAliases:
  smoke:
    - "@smoke"
    - "@sanity"
  critical:
    - "@P0"
    - "@critical"
  api:
    - "@api"
  ui:
    - "@ui"
  slow:
    - "@slow"
    - "@performance"
  flaky:
    - "@flaky"
```

The agent should auto-detect defaults, so this config is optional.

---

## 20.11 Environment Variables

Support these environment variables:

```bash
TESTARA_AGENT_ENABLED=true
TESTARA_AGENT_PROVIDER=openai
TESTARA_AGENT_MODEL=gpt-4.1-mini
TESTARA_AGENT_API_KEY=...
TESTARA_AGENT_RUN_ENABLED=false
TESTARA_AGENT_WRITE_ENABLED=false
TESTARA_AGENT_REQUIRE_CONFIRMATION=true
TESTARA_AGENT_MAX_CONTEXT_FILES=80
TESTARA_AGENT_MAX_LOG_SIZE=2MB
```

Priority order:

```text
1. CLI flags
2. Environment variables
3. testara-agent.yaml
4. configuration.properties
5. built-in defaults
```

---

## 20.12 Security Defaults

MCP and AI assistant integrations can expose local tools to an agent, so secure defaults are mandatory.

Default behavior:

```text
- Read-only skills enabled.
- File writing disabled.
- Test execution disabled or dry-run only.
- External LLM source-code upload disabled unless configured.
- Secret redaction enabled.
- Maven execution restricted to safe templates.
- Shell injection blocked.
```

Recommended permission tiers:

| Tier | Tools | Default |
|---|---|---|
| Read-only | summary, overview, review | Enabled |
| Plan-only | plan, init dry-run | Enabled |
| Write | generated files, apply patches | Disabled |
| Execute | `/test-run` actual execution | Disabled |
| External LLM | send repo context to provider | Disabled unless configured |

### Safe `/test-run` Command Templates

Only allow commands generated from safe templates:

```bash
mvn test -Dcucumber.filter.tags="{tagExpression}"
mvn verify -Dcucumber.filter.tags="{tagExpression}"
mvn -pl {module} test -Dcucumber.filter.tags="{tagExpression}"
mvn -pl {module} verify -Dcucumber.filter.tags="{tagExpression}"
```

Reject:

```text
- arbitrary shell text
- command chaining
- pipes
- redirects
- `&&`
- `||`
- `;`
- backticks
- `$()`
- destructive commands
```

---

## 20.13 Repository Files Users May Add

For projects that want AI assistant integration committed into the repo:

```text
.mcp.json
.vscode/mcp.json
testara-agent.yaml
```

Suggested `.gitignore` entries:

```gitignore
# Testara Agent local outputs
.testara-agent/
target/testara-agent/
```

Do not commit:

```text
.env
API keys
local model credentials
private MCP configs with secrets
```

---

## 20.14 Documentation To Add To Testara

Add a new docs page:

```text
docs/agentic-skills.md
```

Suggested sections:

```text
- What is Testara Agent?
- CLI installation
- CLI usage
- MCP mode
- Claude Code setup
- Cursor setup
- GitHub Copilot setup
- Codex-compatible setup
- Docker setup
- Security defaults
- /test-run safety model
- Troubleshooting
```

Suggested README addition:

```md
## Testara Agent

Testara Agent provides AI-assistant-friendly tools for summarizing, reviewing, generating, initializing, and running Testara tests.

Install:

```bash
curl -L https://github.com/ygrip/testara/releases/latest/download/testara-agent \
  -o ~/.local/bin/testara-agent
chmod +x ~/.local/bin/testara-agent
```

Run:

```bash
testara-agent /test-overview .
testara-agent /test-run --dry-run "run payment smoke tests"
```

MCP mode:

```bash
testara-agent mcp
```
```

---

## 20.15 Troubleshooting

### MCP server not detected

Checklist:

```text
- `testara-agent --version` works.
- `testara-agent mcp` starts without error.
- Java 21 is installed if using JAR mode.
- MCP config path is correct.
- Assistant/client was restarted after config update.
```

### `/test-run` matches zero scenarios

Possible causes:

```text
- Requested tags do not exist.
- Project uses different tag naming.
- Feature root was not detected.
- Tag aliases are not configured.
```

Recommended command:

```bash
testara-agent /test-overview . --format json
```

Then inspect available tags.

### `/test-run` dry-run works but execution is blocked

Check:

```text
- TESTARA_AGENT_RUN_ENABLED=true
- run.enabled=true in testara-agent.yaml
- requireConfirmation setting
- MCP client approval prompt
```

### Generated files are not written

Check:

```text
- Use --apply.
- Set TESTARA_AGENT_WRITE_ENABLED=true.
- Confirm write.enabled=true in testara-agent.yaml.
```

### LLM provider fails

Check:

```text
- TESTARA_AGENT_API_KEY is set.
- Provider/model config is valid.
- Offline mode is not enabled.
- Local LLM server is running if using local provider.
```

---

## 20.16 Recommended Integration Rollout

Implement installation and integration in this order:

```text
1. CLI executable JAR.
2. `testara-agent mcp` stdio server.
3. Claude Code setup docs.
4. VS Code / GitHub Copilot `.vscode/mcp.json` docs.
5. Cursor setup docs.
6. Codex-compatible CLI and MCP notes.
7. Docker image.
8. Native binaries.
9. Maven plugin.
10. Optional IDE plugin.
```

This gives users a smooth path:

```text
CLI for everyone
MCP for AI assistants
Docker for CI and isolation
Native binary for polished install
Maven plugin for Java-native workflows
```

## 19. Final Recommendation

Build this as a **Testara Agent Toolkit**, not as a normal runtime feature.

The clean implementation path is:

```text
1. Build a reliable project index.
2. Add read-only skills.
3. Add review and generation skills.
4. Add safe test execution via /test-run.
5. Add interactive planning and initialization.
6. Expose via CLI first, MCP later.
```

The most important guardrail is that the agent must always distinguish between:

```text
- What exists in the project
- What it inferred
- What it proposes to create
- What it actually executed
```

This keeps Testara agentic assistance useful, auditable, and safe for local and CI usage.
