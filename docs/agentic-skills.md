# Testara Agent — Agentic Skills

Testara Agent is an AI-assisted toolkit that analyzes, reviews, generates, bootstraps, and runs Testara automation assets. It is a **developer tool**, not part of the test execution runtime — it runs alongside your project, reads your code and feature files, and produces structured suggestions, generated scaffolds, or test run reports.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Skills Reference](#skills-reference)
  - [/test-summary](#test-summary)
  - [/test-overview](#test-overview)
  - [/test-review](#test-review)
  - [/test-run](#test-run)
  - [/test-command](#test-command)
  - [/test-validation](#test-validation)
  - [/test-plan](#test-plan)
  - [/test-init](#test-init)
- [MCP Server Mode](#mcp-server-mode)
- [LLM Configuration](#llm-configuration)
- [Environment Variables](#environment-variables)
- [Security Model](#security-model)

---

## Prerequisites

| Requirement | Details |
|---|---|
| Java 21+ | Runtime for the agent JAR |
| A Testara project | Must have a `pom.xml` at the project root |
| Maven 3.9+ | Required for `/test-run` execution mode |
| (Optional) OpenAI-compatible API key | Enables LLM-assisted generation skills |

Testara Agent **does not require** Selenium, a browser, Appium, or any external test server to run the read-only skills (`/test-summary`, `/test-overview`, `/test-review`). LLM integration is optional and disabled by default.

---

## Installation

### Option A — Build from source (current)

```bash
cd testara
mvn -pl testara-agent-cli -am package -DskipTests
# Fat JAR produced at:
ls testara-agent-cli/target/testara-agent-cli.jar
```

Set a shell alias for convenience:

```bash
alias testara-agent='java -jar /path/to/testara-agent-cli.jar'
```

Verify:

```bash
testara-agent --version
# testara-agent 2.0.0
```

### Option B — Future: native binary / GitHub Releases

Native binaries and a GitHub Packages artifact are planned. Until then, use the fat JAR.

---

## Quick Start

From your Testara project root:

```bash
# See your project's test coverage at a glance
testara-agent /test-overview .

# Summarize a specific feature file
testara-agent /test-summary src/test/resources/features/payment/checkout.feature

# Review a directory for quality issues
testara-agent /test-review src/test/resources/features/payment

# Dry-run a tag-based test execution
testara-agent /test-run "run payment smoke tests"

# Generate a new command class
testara-agent /test-command "generate customer id with prefix CUS and timestamp"
```

---

## Skills Reference

### /test-summary

Summarizes feature files at scenario, feature, or directory level. **Read-only. No LLM required.**

```bash
testara-agent /test-summary <path> [--scenario "scenario name filter"]
```

**What it shows:**
- Feature name, file path, and tags
- All scenarios with type (Scenario / Scenario Outline), tags, and steps
- Background steps
- Example rows for Scenario Outlines
- Step counts, total summary stats

**Examples:**

```bash
# Summarize a single feature file
testara-agent /test-summary src/test/resources/features/login/login.feature

# Summarize an entire domain
testara-agent /test-summary src/test/resources/features/payment

# Filter to one specific scenario
testara-agent /test-summary src/test/resources/features/payment/checkout.feature \
  --scenario "Successful checkout with valid card"
```

**Sample output:**
```
# Test Summary: checkout.feature

**Feature files:** 1
**Scenarios:** 4
**Scenario Outlines:** 1 (6 example rows)
**Tags:** @api, @checkout, @P1, @regression

## Checkout API
`src/test/resources/features/payment/checkout.feature`

### Successful checkout with valid card
Tags: @P1 @positive @regression
- Given a customer with a valid cart
- When the customer submits a checkout request
- Then the response status should be 200
- And the order should be created with status PENDING
```

---

### /test-overview

Produces a statistical overview of the entire test project. **Read-only. No LLM required.**

```bash
testara-agent /test-overview [path] [--format markdown|json]
```

**What it shows:**
- Feature files, scenarios, outlines, example rows, total steps
- Step definitions indexed, custom commands, custom validators
- Average steps per scenario, longest scenarios
- Tag distribution table (sorted by usage)
- Custom command and validator catalog

**Examples:**

```bash
# Overview of the whole project
testara-agent /test-overview .

# Overview of a specific feature root
testara-agent /test-overview src/test/resources/features

# Machine-readable JSON
testara-agent /test-overview . --format json
```

**Sample output:**
```
# Testara Project Overview

**Project root:** `/home/user/automation`
**Build tool:** MAVEN
**Java version:** 21

## Coverage

| Metric | Count |
|---|---|
| Feature files | 42 |
| Scenarios | 318 |
| Scenario Outlines | 74 |
| Example rows | 1,240 |
| Total steps | 2,037 |
| Step definitions | 156 |
| Custom commands | 12 |
| Custom validators | 8 |
| Avg steps/scenario | 6.4 |

## Tag Distribution

| Tag | Features | Scenarios |
|---|---|---|
| @api | 28 | 201 |
| @ui | 12 | 87 |
| @regression | 40 | 211 |
| @smoke | 10 | 22 |
```

---

### /test-review

Reviews feature files and step definitions for quality issues. **Read-only. No LLM required.**

```bash
testara-agent /test-review <path>
```

**What it detects:**

| Severity | Finding |
|---|---|
| HIGH | Duplicate scenario names across files |
| HIGH | Scenarios with no `Then` assertion |
| MEDIUM | Scenarios with > 10 steps (high complexity) |
| MEDIUM | Near-duplicate step sequences (≥ 70% shared steps) |
| LOW | Scenarios with no tags |
| INFO | All scenarios share the same first step → suggest `Background` |
| INFO | Multiple scenarios with identical step structure → suggest `Scenario Outline` |

After the findings, priority recommendations are shown:

```
## Priority Recommendations

- **P0** — Fix 2 BLOCKER issue(s) before any release.
- **P1** — Resolve 5 HIGH issue(s) in the next sprint.
- **P2–P3** — Schedule remaining MEDIUM/LOW findings for backlog grooming.
```

**Examples:**

```bash
# Review a single feature
testara-agent /test-review src/test/resources/features/checkout.feature

# Review all features in a domain
testara-agent /test-review src/test/resources/features/payment

# Review step definitions
testara-agent /test-review src/test/java/com/company/automation/steps
```

**Sample output excerpt:**
```
# Test Review: payment

**Feature files reviewed:** 6
**Total findings:** 11

## HIGH (3)

**[HIGH]** Duplicate scenario name: "approve pending refund"
> Found in 2 locations. Duplicate names hide coverage gaps and make reports ambiguous.
_refund.feature_ — `approve pending refund`
Suggestion: Rename each scenario to reflect its specific intent.

## MEDIUM (5)

**[MEDIUM]** High-complexity scenario (14 steps)
> "Full payment lifecycle with retry" has 14 steps. Long scenarios are hard to maintain.
_payment.feature_ — `Full payment lifecycle with retry`
Suggestion: Split into smaller focused scenarios or extract common steps into a Background.
```

---

### /test-run

Resolves a natural-language request into a Cucumber tag expression and optionally executes it. **Dry-run by default.**

```bash
testara-agent /test-run "<intent>" [--execute] [--module <module>] [--report markdown|json] [--project .]
```

**Tag resolution priority:**
1. Explicit `@tags` in the input → used directly
2. Known aliases (`smoke`, `regression`, `api`, `ui`, `critical`, `p0`, `flaky`, `slow`) → mapped
3. OR groups (`"payment or order"`) → `(@payment or @order)`
4. NOT clauses (`"except slow"`, `"not flaky"`) → `not @slow`
5. Indexed project tags → matched against the project's actual tag inventory
6. Unresolvable → shows available tags

**Execution is blocked by default.** To enable:
```bash
export TESTARA_AGENT_RUN_ENABLED=true
```

Execution enforces a **15-minute timeout** and auto-detects `./mvnw` vs `mvn`.

**Examples:**

```bash
# Dry-run only (default) — shows resolved expression and scenario count
testara-agent /test-run "run payment smoke tests"

# Dry-run with explicit tags
testara-agent /test-run "run @payment and @smoke tests"

# OR groups
testara-agent /test-run "run payment or order regression tests"

# Exclude slow tests
testara-agent /test-run "run api regression except slow"

# Execute (requires TESTARA_AGENT_RUN_ENABLED=true)
export TESTARA_AGENT_RUN_ENABLED=true
testara-agent /test-run "run payment smoke tests" --execute

# Restrict to a Maven module
testara-agent /test-run "run smoke tests" --execute --module payment-tests

# JSON report
testara-agent /test-run "run payment smoke tests" --execute --report json
```

**Sample dry-run output:**
```
## Test Run Plan

**Intent:** run payment smoke tests
**Resolved tag expression:** `@payment and @smoke`
**Matched features:** 3
**Matched scenarios:** 12
**Command:**
```
mvn test -Dcucumber.filter.tags="@payment and @smoke"
```
```

**Allowed Maven command templates (no injection possible):**
```
mvn test -Dcucumber.filter.tags="..."
mvn verify -Dcucumber.filter.tags="..."
mvn -pl <module> test -Dcucumber.filter.tags="..."
```

---

### /test-command

Generates a Testara `CommandLogic<T>` class and unit test from a natural-language description.

```bash
testara-agent /test-command "<description>" [--package <pkg>] [--return-type <type>] [--project .]
```

**What it generates:**
- A `CommandLogic<T>` class with `@CommandTag` and `preProcessParameters()` + `execute()` stubs
- A JUnit 5 unit test skeleton
- Package placement path
- Required `command.executor.scan-locations` config

**Examples:**

```bash
testara-agent /test-command "generate a customer code with prefix CUS and current timestamp"

testara-agent /test-command "mask an email address but keep domain visible" \
  --package com.company.automation.commands \
  --return-type String

testara-agent /test-command "generate random UUID in uppercase" \
  --package com.company.automation.commands \
  --return-type String
```

**Sample output:**
````
## Generated Command: `generate-a-customer-code-with-prefix-cus-and-current-tim`

### GenerateACustomerCodeWithPrefixCusAndCurrentTimCommand.java

```java
package com.company.automation.commands;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import java.util.List;

@CommandTag(command = "generate-a-customer-code-with-prefix-cus-and-current-tim")
public class GenerateACustomerCodeWithPrefixCusAndCurrentTimCommand implements CommandLogic<String> {

  @Override
  public boolean preProcessParameters() { return false; }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    // TODO: implement command logic
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
```

### Scan Location Config

```properties
command.executor.scan-locations=io.github.ygrip.testara,com.company.automation.commands
```
````

---

### /test-validation

Generates a Testara validation — either a JSON spec using a built-in validator, or a custom `ValidatorLogic` Java class.

```bash
testara-agent /test-validation "<description>" [--mode auto|json|java] [--package <pkg>] [--project .]
```

**Mode selection:**
- `auto` (default) — tries JSON first; falls back to Java if no built-in validator matches
- `json` — always generates a JSON validation file
- `java` — always generates a custom `ValidatorLogic` class

**Built-in validators recognized:**
`EQUAL`, `NOT_EQUAL`, `EMPTY`, `NOT_EMPTY`, `CONTAINS`, `CONTAINS_TEXT`, `STARTS_WITH`, `ENDS_WITH`, `MATCH_PATTERN`, `HAS_SIZE`, `GREATER_THAN`, `LESSER_THAN`, `IN_RANGE_OF`, `SORTED`, `CONTAINS_KEY`, `MATCH_SCHEMA`

**Examples:**

```bash
# Auto-detects SORTED validator
testara-agent /test-validation "response should contain users sorted by createdDate descending"

# Auto-detects MATCH_PATTERN validator
testara-agent /test-validation "every email in the response should match a valid email pattern"

# Force Java class for domain-specific logic
testara-agent /test-validation \
  "validate that transaction amount is within 0.01 tolerance of expected" \
  --mode java \
  --package com.company.automation.validators
```

**Sample JSON output:**
```
## Generated Validation

**Description:** response should contain users sorted by createdDate descending
**Mode:** JSON (uses built-in validator `SORTED`)

### validation.json

```json
{
  "validation": "SORTED",
  "description": "response should contain users sorted by createdDate descending",
  "expected": null
}
```

**Placement:** `src/test/resources/validations/<domain>/<name>.json`
```

---

### /test-plan

Generates a Testara-compatible Cucumber `.feature` file grounded in your actual project's step definitions. Unknown steps are explicitly marked `# MISSING`.

```bash
testara-agent /test-plan "<intent>" [--slice api|ui|database|streaming|fullstack] [--domain <domain>] [--tag <tag>] [--project .]
```

**Placement by slice:**

| Slice | Feature path |
|---|---|
| `api` | `src/test/resources/features/api/<domain>/` |
| `ui` | `src/test/resources/features/ui/<domain>/` |
| `database` | `src/test/resources/features/database/<domain>/` |
| `streaming` | `src/test/resources/features/streaming/<domain>/` |

**Guardrails:**
- Steps are validated against the indexed step definition catalog
- Unknown steps get `# MISSING` comment — they must be implemented before the feature can run
- Secret patterns are not generated into files
- One positive (`@P1 @positive`) and one negative (`@P2 @negative`) scenario always produced

**Examples:**

```bash
testara-agent /test-plan "Create tests for refund approval flow" --slice api

testara-agent /test-plan "Test login with invalid credentials" \
  --slice ui \
  --domain auth \
  --tag smoke

testara-agent /test-plan "Validate order settlement event on Kafka" --slice streaming

testara-agent /test-plan "Verify payment capture API with 3DS" \
  --slice api \
  --domain payment \
  --tag regression --tag critical
```

**Sample output:**
````
## Test Plan: Create tests for refund approval flow

**Slice:** api
**Domain:** refund
**Placement:** `src/test/resources/features/api/refund/`

> **Review before committing.** Steps marked `# MISSING` need step definitions.

### Generated Feature

```gherkin
# Generated by Testara Agent.
# Source request: Create tests for refund approval flow
# Review before committing.

@api @refund @regression
Feature: Create tests for refund approval flow

  Background:
    Given the API service "refund-service" is available # MISSING

  @P1 @positive
  Scenario: Create tests for refund approval flow — happy path
    Given a valid request to create refund # MISSING
    When the request is sent # MISSING
    Then the response status should be 200 # MISSING
    And the response should contain the expected refund data # MISSING

  @P2 @negative
  Scenario: Create tests for refund approval flow — failure case
    Given a request to create refund with invalid data # MISSING
    When the request is sent # MISSING
    Then the response status should be 400 # MISSING
    And the response should contain an error message # MISSING
```

### Missing Step Definitions

- `the API service "refund-service" is available`
- `a valid request to create refund`
...

These steps must be implemented before the feature can run.
````

---

### /test-init

Bootstraps a new Testara automation project or generates the minimal patch to integrate Testara into an existing Maven project.

```bash
testara-agent /test-init [--type api|ui|database|streaming|fullstack] \
  [--base-package <pkg>] \
  [--engine selenium|playwright|appium] \
  [--integrate-existing] \
  [--project .]
```

**Modes:**
- Default: full project bootstrap — generates `pom.xml`, `configuration.properties`, runner, directories
- `--integrate-existing`: shows only the missing additions for an existing project

**Examples:**

```bash
# Bootstrap a new API-only project
testara-agent /test-init --type api --base-package com.company.automation

# Bootstrap a UI project with Playwright
testara-agent /test-init --type ui --engine playwright --base-package com.company.automation

# Integrate Testara into an existing Maven project
testara-agent /test-init --integrate-existing --project .

# Full-stack project
testara-agent /test-init --type fullstack --base-package com.company.automation
```

**What is generated:**

| File | Purpose |
|---|---|
| `pom.xml` | Testara BOM + module dependencies |
| `src/test/resources/configuration.properties` | Cucumber glue, feature root, reporter config |
| `src/test/resources/features/` | Feature file root |
| `src/test/resources/files/` | Request spec JSON root |
| `src/test/resources/validations/` | Validation spec root |
| `src/test/java/<pkg>/runner/TestRunner.java` | JUnit Platform suite runner |
| `src/test/java/<pkg>/steps/StepDefinitions.java` | Step definition placeholder |
| `src/test/java/<pkg>/pages/BasePage.java` | (UI only) Page object base |
| `src/test/java/<pkg>/actions/BaseAction.java` | (UI only) Action base |

---

## MCP Server Mode

Testara Agent can expose all skills as MCP tools, making them available inside Claude Code, Cursor, GitHub Copilot, and any MCP-compatible AI assistant.

### Start the MCP server

```bash
# Using the fat JAR
java -jar testara-agent-cli.jar mcp [project-root]

# Or via alias
testara-agent mcp .
```

The server reads requests from `stdin` and writes JSON-RPC 2.0 responses to `stdout`. Errors go to `stderr`.

### Claude Code integration

Add to `.mcp.json` in your project root:

```json
{
  "mcpServers": {
    "testara": {
      "command": "java",
      "args": ["-jar", "/path/to/testara-agent-cli.jar", "mcp", "."],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "false",
        "TESTARA_AGENT_WRITE_ENABLED": "false"
      }
    }
  }
}
```

Or with shell alias:

```json
{
  "mcpServers": {
    "testara": {
      "command": "testara-agent",
      "args": ["mcp", "."]
    }
  }
}
```

### Cursor integration

Add to `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "testara": {
      "command": "testara-agent",
      "args": ["mcp", "."]
    }
  }
}
```

### VS Code / GitHub Copilot

Add to `.vscode/mcp.json`:

```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "testara-agent",
      "args": ["mcp", "."],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "false"
      }
    }
  }
}
```

### Available MCP tools

| Tool name | Skill |
|---|---|
| `testara_summary` | `/test-summary` |
| `testara_overview` | `/test-overview` |
| `testara_review` | `/test-review` |
| `testara_run` | `/test-run` (dry-run by default) |
| `testara_command` | `/test-command` |
| `testara_validation` | `/test-validation` |
| `testara_plan` | `/test-plan` |
| `testara_init` | `/test-init` |

### Example prompts in Claude Code

```
Use Testara to summarize the test coverage for this project.
```

```
Use Testara to review duplicated scenarios in src/test/resources/features/payment.
```

```
Use Testara to show me all @smoke scenarios and how many there are.
```

```
Use Testara to generate a test plan for the order cancellation API flow.
```

```
Use Testara to dry-run "run payment regression except slow tests".
```

---

## LLM Configuration

Generation skills (`/test-command`, `/test-validation`, `/test-plan`, `/test-init`) work without an LLM using deterministic template-based generation. Providing an LLM API key upgrades the quality of generated output.

### Configuration via environment variables

```bash
# Provider (openai-compatible endpoint)
export TESTARA_AGENT_PROVIDER=openai
export TESTARA_AGENT_MODEL=gpt-4.1-mini
export TESTARA_AGENT_API_KEY=sk-...

# Optional overrides
export TESTARA_AGENT_BASE_URL=https://api.openai.com/v1
export TESTARA_AGENT_TEMPERATURE=0.2
export TESTARA_AGENT_MAX_CONTEXT_FILES=80
```

### Supported providers

Any OpenAI-compatible endpoint works:
- **OpenAI** — `https://api.openai.com/v1` (default)
- **Azure OpenAI** — set `TESTARA_AGENT_BASE_URL` to your Azure endpoint
- **Ollama** — `http://localhost:11434/v1` (local models)
- **GitHub Models** — set base URL to GitHub's model endpoint

### Secret redaction

Before any content is sent to the LLM provider, the following patterns are automatically redacted:

```
password=...    → password=[REDACTED]
token=...       → token=[REDACTED]
api-key=...     → api-key=[REDACTED]
authorization=  → authorization=[REDACTED]
private-key=    → private-key=[REDACTED]
cookie=         → cookie=[REDACTED]
session=        → session=[REDACTED]
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TESTARA_AGENT_RUN_ENABLED` | `false` | Allow `/test-run` to execute Maven |
| `TESTARA_AGENT_PROVIDER` | `openai` | LLM provider |
| `TESTARA_AGENT_MODEL` | `gpt-4.1-mini` | LLM model |
| `TESTARA_AGENT_API_KEY` | — | LLM API key (required for LLM features) |
| `TESTARA_AGENT_BASE_URL` | `https://api.openai.com/v1` | LLM base URL |
| `TESTARA_AGENT_TEMPERATURE` | `0.2` | LLM temperature |
| `TESTARA_AGENT_MAX_CONTEXT_FILES` | `80` | Max files included in LLM context |

---

## Security Model

| Capability | Default | Enable with |
|---|---|---|
| Read-only skills (`/test-summary`, `/test-overview`, `/test-review`) | **Enabled** | — |
| Plan-only skills (`/test-run --dry-run`, `/test-plan`, `/test-init`) | **Enabled** | — |
| Test execution (`/test-run --execute`) | **Blocked** | `TESTARA_AGENT_RUN_ENABLED=true` |
| LLM context upload | **Disabled** | `TESTARA_AGENT_API_KEY=...` |
| Shell injection | **Blocked** | Cannot be enabled — hardcoded |

### Test execution safety

`/test-run` only builds Maven commands from safe templates. The following shell characters are **always rejected**:

```
&&  ||  ;  |  >  <  `  $()  backticks
```

Only these command shapes are ever produced:

```
mvn test -Dcucumber.filter.tags="..."
mvn verify -Dcucumber.filter.tags="..."
mvn -pl <module> test -Dcucumber.filter.tags="..."
```

Execution is additionally subject to a 15-minute hard timeout.
