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
  - [knowledge](#knowledge)
- [Project Configuration](#project-configuration)
- [Knowledge Store](#knowledge-store)
- [MCP Server Mode](#mcp-server-mode)
- [LLM Configuration](#llm-configuration)
- [Docker](#docker)
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

### Option A — Build from source

```bash
cd testara
mvn -pl testara-agent-cli -am package -DskipTests
# Fat JAR: testara-agent-cli/target/testara-agent.jar
```

```bash
alias testara-agent='java -jar /path/to/testara-agent.jar'
testara-agent --version   # testara-agent 2.0.0
```

### Option B — One-line install script

```bash
curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash
testara-agent --version
```

### Option C — Download binary (GitHub Releases)

```bash
# Fat JAR
curl -LO https://github.com/ygrip/testara/releases/latest/download/testara-agent.jar
java -jar testara-agent.jar --version

# Native binary (Linux/macOS/Windows)
curl -LO https://github.com/ygrip/testara/releases/latest/download/testara-agent-darwin-arm64
chmod +x testara-agent-darwin-arm64
./testara-agent-darwin-arm64 --version
```

### Option D — Docker

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace \
  ghcr.io/ygrip/testara-agent:latest /test-overview .
```

---

## Quick Start

From your Testara project root:

```bash
# Project overview at a glance
testara-agent /test-overview .

# Summarize a specific feature file
testara-agent /test-summary src/test/resources/features/payment/checkout.feature

# Review for quality issues (duplicates, complexity, missing tags)
testara-agent /test-review src/test/resources/features/payment

# Dry-run a tag-based test execution
testara-agent /test-run "run payment smoke tests"

# Re-run previously failed scenarios
testara-agent /test-run --rerun-failed

# Generate a new command class
testara-agent /test-command "generate customer id with prefix CUS and timestamp"

# Manage knowledge cache
testara-agent knowledge status
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

### knowledge

Manages the project knowledge cache (fingerprint-based incremental indexing).

```bash
testara-agent knowledge status    # Show cache state, file/scenario counts
testara-agent knowledge refresh   # Force full re-index
testara-agent knowledge clear     # Wipe cache
```

Example output:
```
Testara Knowledge Status

Cache: .testara-agent/knowledge
Storage: JSONL
Status: fresh
Tracked files: 214
Features: 42
Scenarios: 318
Step definitions: 126
Commands: 12
Validations: 8
Tags: 31
```

---

## Project Configuration

Optional `testara-agent.yaml` at the project root. Priority: CLI flags > env vars > YAML > `configuration.properties` > defaults.

```yaml
run:
  enabled: false
  requireConfirmation: true
  timeout: 15m

llm:
  provider: openai
  model: gpt-4.1-mini
  redactSecrets: true

tagAliases:
  smoke: ["@smoke", "@sanity"]
  critical: ["@P0", "@critical"]
  api: ["@api"]
  ui: ["@ui"]
  slow: ["@slow", "@performance"]
  flaky: ["@flaky"]
```

---

## Knowledge Store

Persistent JSONL cache under `.testara-agent/knowledge/` for fast repeated invocations.

1. **First run** — full index, saves fingerprints + manifest
2. **Subsequent runs** — compares file fingerprints (path + size + lastModified)
3. **No changes** — reuses cache (sub-second startup)
4. **Build/config change** — full reindex
5. **Any error** — safe fallback to direct `ProjectIndexer`

Cache files: `manifest.json`, `file-fingerprints.jsonl`
File types tracked: `BUILD`, `FEATURE`, `STEP_DEFINITION`, `COMMAND`, `VALIDATION`, `REQUEST_SPEC`, `VALIDATION_FILE`, `CONFIG`, `OTHER`

Add to `.gitignore`: `.testara-agent/`

---

## MCP Server Mode

Exposes all 8 skills as MCP tools + 8 prompts via stdio JSON-RPC 2.0.

```bash
testara-agent mcp [project-root]
```

### Available MCP tools

| Tool | Skill |
|---|---|
| `testara_summary` | `/test-summary` |
| `testara_overview` | `/test-overview` |
| `testara_review` | `/test-review` |
| `testara_run` | `/test-run` (dry-run by default) |
| `testara_command` | `/test-command` |
| `testara_validation` | `/test-validation` |
| `testara_plan` | `/test-plan` |
| `testara_init` | `/test-init` |

MCP prompts are also exposed: `test-summary`, `test-review`, `test-plan`, `test-command`, `test-validation`, `test-init`, `test-overview`, `test-run`.

### Claude Code
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

Generation skills work **without an LLM** using deterministic template-based generation. Providing an API key upgrades output quality.

### OpenAI

```bash
export TESTARA_AGENT_PROVIDER=openai
export TESTARA_AGENT_MODEL=gpt-4.1-mini
export TESTARA_AGENT_API_KEY=sk-...
```

### Ollama (local models)

```bash
export TESTARA_AGENT_BASE_URL=http://localhost:11434
export TESTARA_AGENT_MODEL=llama3
# No API key needed — LocalLlmClient uses Ollama's /api/generate endpoint
```

### Secret redaction

Before content reaches the LLM, `SecretRedactionGuard` scrubs PEM blocks, Bearer tokens, AWS keys, `key=value` secrets, and JDBC connection credentials.

---

## Docker

```bash
docker build -t testara-agent .
docker run --rm -v "$PWD:/workspace" -w /workspace testara-agent /test-overview .
docker run -i --rm -v "$PWD:/workspace" -w /workspace testara-agent mcp
```

Multi-stage: `maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre-alpine` runtime, non-root user.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TESTARA_AGENT_RUN_ENABLED` | `false` | Allow `/test-run --execute` |
| `TESTARA_AGENT_WRITE_ENABLED` | `false` | Allow file writes (`--apply`) |
| `TESTARA_AGENT_PROVIDER` | `openai` | LLM provider (`openai` or `ollama`) |
| `TESTARA_AGENT_MODEL` | `gpt-4.1-mini` | LLM model name |
| `TESTARA_AGENT_API_KEY` | — | OpenAI-compatible API key |
| `TESTARA_AGENT_BASE_URL` | `https://api.openai.com/v1` | LLM base URL |
| `TESTARA_AGENT_TEMPERATURE` | `0.2` | LLM temperature |
| `TESTARA_AGENT_MAX_CONTEXT_FILES` | `80` | Max files in LLM context |
| `TESTARA_AGENT_MAX_OUTPUT_FILES` | `20` | Max generated output files |

---

## Security Model

| Capability | Default | Enable with |
|---|---|---|
| Read-only skills | **Enabled** | — |
| Plan-only skills | **Enabled** | — |
| File writes | **Blocked** | `TESTARA_AGENT_WRITE_ENABLED=true` |
| Test execution | **Blocked** | `TESTARA_AGENT_RUN_ENABLED=true` |
| LLM context upload | **Disabled** | `TESTARA_AGENT_API_KEY=...` |
| Shell injection | **Blocked** | Cannot be enabled — hardcoded |

### Safety Guards

| Guard | Purpose |
|---|---|
| `SecretRedactionGuard` | Scrubs PEM keys, Bearer tokens, AWS keys, passwords, JDBC credentials before LLM upload |
| `OutputValidator` | Validates generated Gherkin syntax, Java class structure, and JSON format |
| `FeaturePlacementGuard` | Prevents duplicate scenario names in target directories |
| `JavaCompilationGuard` | Checks class declarations, `@CommandTag`/`@ValidationTag` annotations |
| `TestExecutionGuard` | Rejects shell injection, validates tag expressions, checks project root |

### Test execution safety

Only safe Maven command templates are produced. Rejected patterns: `&&` `||` `;` `|` `>` `<` `` ` `` `$()` backticks.

Allowed templates: `mvn test -Dcucumber.filter.tags="..."`, `mvn verify -Dcucumber.filter.tags="..."`, `mvn -pl <module> test -Dcucumber.filter.tags="..."`. 15-minute hard timeout.

### Parsers & Indexers

| Component | Description |
|---|---|
| `FeatureParser` | Line-based Gherkin parser with step data table support |
| `JavaStepParser` | Scans method signatures, params, throws clauses, Javadoc from step `.java` files |
| `CucumberReportParser` | Parses `cucumber.json` + JUnit XML with failed scenario extraction |
| `ProjectIndexer` | Full project scanner (modules, features, steps, commands, validations, drivers, tags) |
| `IncrementalIndexer` | Fingerprint-aware reindex decisions (cache reuse vs full reindex) |
| `KnowledgeQueryService` | Structured queries over cached project knowledge |
