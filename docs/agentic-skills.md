# Testara Agent — Agentic Skills Guide

Testara Agent is an AI-assisted CLI and MCP server that scaffolds, reviews, plans, and executes Testara automation projects. It understands the Testara runtime — properties, commands, validations, request specs, pages, actions, and Cucumber steps — and generates artifacts that follow Testara conventions without writing generic Cucumber glue.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [3-Step Quick Start](#3-step-quick-start)
- [MCP Integration](#mcp-integration)
- [Skills Reference](#skills-reference)
  - [test-init](#test-init)
  - [test-plan](#test-plan)
  - [test-run](#test-run)
  - [test-command](#test-command)
  - [test-validation](#test-validation)
  - [test-overview](#test-overview)
  - [test-summary](#test-summary)
  - [test-review](#test-review)
  - [testara-context](#testara-context)
  - [testara-property](#testara-property)
  - [testara-api](#testara-api)
  - [testara-ui](#testara-ui)
  - [testara-db](#testara-db)
  - [testara-guide](#testara-guide)
  - [knowledge](#knowledge)
- [Generation Rules](#generation-rules)
- [Runtime Chain](#runtime-chain)
- [Environment Variables](#environment-variables)
- [Security Model](#security-model)

---

## Prerequisites

| Requirement | Notes |
|---|---|
| Java 21+ | Required to run the agent JAR |
| Maven 3.9+ | Required for `test-run --execute` and `test-init --write` compile gate |
| jenv (optional) | To select Java 21 when multiple versions are installed |

---

## Installation

### One-line install

```sh
curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash
```

The installer:
1. Downloads the agent JAR to `~/.testara/testara-agent.jar`
2. Writes a `~/.local/bin/testara-agent` wrapper (honors `$JAVA_HOME`)
3. Adds the bin dir to your shell profile
4. **Automatically configures MCP** for VS Code, Cursor, Claude Desktop, and Claude Code

### Manual install

```sh
# Download JAR
curl -fsSL https://github.com/ygrip/testara/releases/latest/download/testara-agent.jar \
  -o ~/.testara/testara-agent.jar

# Write wrapper
cat > ~/.local/bin/testara-agent << 'EOF'
#!/usr/bin/env sh
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA_CMD" -jar "$HOME/.testara/testara-agent.jar" "$@"
EOF
chmod +x ~/.local/bin/testara-agent
```

### Build from source

```sh
mvn -pl testara-agent-cli -am package -DskipTests -B
cp testara-agent-cli/target/testara-agent.jar ~/.testara/testara-agent.jar
```

---

## 3-Step Quick Start

After installing, create and run a full automation project in three commands:

```sh
# 1. Scaffold the project (interactive — asks for group ID, artifact, type)
mkdir my-tests && cd my-tests
testara-agent test-init

# 2. Generate a Cucumber feature
testara-agent test-plan 'test the login flow' --write

# 3. Execute the tests
TESTARA_AGENT_RUN_ENABLED=true testara-agent test-run 'login flow' --execute
```

### Non-interactive init

```sh
testara-agent test-init --group-id com.company --artifact-id payment-tests \
  --type api --yes
```

---

## MCP Integration

Testara Agent exposes all skills as MCP tools via stdio JSON-RPC 2.0.

### Auto-configured providers

The installer adds the MCP config to all detected providers automatically:

| Provider | Config location |
|---|---|
| VS Code | `~/Library/Application Support/Code/User/mcp.json` (macOS) |
| Cursor | `~/Library/Application Support/Cursor/User/mcp.json` (macOS) |
| Claude Desktop | `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) |
| Claude Code | `~/.claude/settings.json` |

### Manual MCP config

**VS Code / Cursor** — add to `~/Library/Application Support/Code/User/mcp.json`:

```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "/Users/<you>/.local/bin/testara-agent",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "true",
        "JAVA_HOME": "/path/to/java21"
      }
    }
  }
}
```

**Per-workspace config** — create `.vscode/mcp.json` in your project:

```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "/Users/<you>/.local/bin/testara-agent",
      "args": ["mcp", "${workspaceFolder}"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "true",
        "JAVA_HOME": "/path/to/java21"
      }
    }
  }
}
```

**Claude Desktop** — add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "testara": {
      "command": "/Users/<you>/.local/bin/testara-agent",
      "args": ["mcp"],
      "env": {
        "TESTARA_AGENT_RUN_ENABLED": "true"
      }
    }
  }
}
```

### jenv / Java version

If your Java 21 is managed by jenv, the wrapper uses `$JAVA_HOME` automatically:

```json
"env": {
  "JAVA_HOME": "/Users/<you>/.jenv/versions/21"
}
```

### Agent workflow in MCP mode

When an AI agent uses Testara via MCP, it should follow this sequence:

```
1. testara_guide          → load generation rules (call once per session)
2. testara_context        → understand installed slices and config
3. testara_property       → check/generate required properties
4. testara_api / testara_ui / testara_db  → generate slice config if needed
5. testara_plan           → generate the feature
6. testara_run            → execute tests
```

---

## Skills Reference

### test-init

Bootstrap a new Testara project with slice-aware scaffold.

```sh
testara-agent test-init                          # interactive
testara-agent test-init --yes                    # skip prompts, use defaults
testara-agent test-init \
  --group-id com.company \
  --artifact-id payment-tests \
  --type api \
  --base-package com.company.payment.automation \
  --yes
testara-agent test-init --type ui                # Selenium UI project
testara-agent test-init --preview                # show files without writing
```

**Interactive prompts:**

```
  Group ID    [io.github.ygrip]:  com.company
  Artifact ID [my-tests]:         payment-tests
  Project type (api/ui/sql/mongo/kafka/fullstack) [api]: api
  Base package [com.company.paymenttests]: com.company.payment.automation
```

**Generated structure (API):**

```
pom.xml
src/test/resources/configuration.properties
src/test/resources/features/api/sample.feature
src/test/resources/files/sample/request/sample-get.json
src/test/java/{pkg}/runner/TestRunner.java
src/test/java/{pkg}/steps/StepDefinitions.java
src/test/java/{pkg}/commands/
src/test/java/{pkg}/validations/
```

After writing, runs `mvn test-compile` and reports the result.

**MCP tool:** `testara_init`

---

### test-plan

Generate a Testara-flavor Cucumber feature file from natural language.

```sh
testara-agent test-plan 'approve refund API'
testara-agent test-plan 'login UI test' --slice ui --write
testara-agent test-plan 'settlement DB validation' --slice sql --write
testara-agent test-plan 'payment kafka event' --slice kafka
```

**Output includes:**
- Testara-flavor steps using `[api]`, `[sql]`, `[mongo]`, `[kafka]`, or `UIBaseSteps`
- `properties()` expressions for all env-specific values
- Request spec path (`files/{domain}/request/{flow}`) instead of inline params
- **Testara Flavor Score** — % of steps using built-in Testara steps
- **Runtime Context Score** — % of generated values correctly using `properties()`
- **Guardrail warnings** if hardcoded URLs or credentials are detected

**`--write` flag:** writes the `.feature` file and (for API) generates request spec + payload stubs.

**MCP tool:** `testara_plan`

---

### test-run

Resolve natural language test intent to a Cucumber tag expression and optionally execute.

```sh
testara-agent test-run 'run payment smoke tests'              # dry-run (default)
testara-agent test-run 'all regression tests' --execute       # execute
testara-agent test-run 'rerun failed'  --rerun-failed         # rerun from rerun.txt
```

Requires `TESTARA_AGENT_RUN_ENABLED=true` to execute.

**MCP tool:** `testara_run`

---

### test-command

List, inspect, or generate Testara command classes.

```sh
testara-agent test-command                          # list all indexed commands
testara-agent test-command detail:uuid              # show source and usage
testara-agent test-command --detail uuid            # same as above
testara-agent test-command 'generate customer code' # generate new command
```

**Available built-in commands** (partial):

| Command | Returns | Description |
|---|---|---|
| `uuid()` | String | Random UUID |
| `timestamp()` | Long | Current timestamp |
| `properties(key)` | String | Read from configuration.properties |
| `prop(key)` | String | Alias for properties() |
| `combine(a,b)` | String | Concatenate values |
| `randomNumber(min,max)` | Integer | Random number |
| `jsonPath(json, path)` | Object | Extract JSON value |
| `request()` | Object | Current API request |
| `response()` | Object | Last API response |

**MCP tools:** `testara_command`, `testara_command_detail`

---

### test-validation

List, inspect, or generate Testara validation classes.

```sh
testara-agent test-validation                        # list all validations
testara-agent test-validation detail:EQUAL           # show built-in validator
testara-agent test-validation 'response matches schema' --mode java
```

**Built-in validators:** `EQUAL`, `NOT_EQUAL`, `CONTAINS`, `CONTAINS_TEXT`, `STARTS_WITH`, `ENDS_WITH`, `EMPTY`, `NOT_EMPTY`, `MATCH_PATTERN`, `HAS_SIZE`, `GREATER_THAN`, `LESSER_THAN`, `IN_RANGE_OF`, `SORTED`, `CONTAINS_KEY`, `MATCH_SCHEMA`

**MCP tools:** `testara_validation`, `testara_validation_detail`

---

### test-overview

Statistical overview of a Testara project.

```sh
testara-agent test-overview .
testara-agent test-overview . --format json
```

**MCP tool:** `testara_overview`

---

### test-summary

Summarize feature files at scenario, feature, or directory level.

```sh
testara-agent test-summary src/test/resources/features
testara-agent test-summary src/test/resources/features/api/payment.feature
```

**MCP tool:** `testara_summary`

---

### test-review

Review feature files for quality issues and Testara flavor compliance.

```sh
testara-agent test-review src/test/resources/features
```

**Detected issues:**
- Duplicate scenario names
- Scenarios without Then assertions
- High-complexity scenarios (>10 steps)
- Untagged scenarios
- Near-duplicate step sequences (→ Scenario Outline)
- Repeated setup steps (→ Background)
- Generic steps where Testara built-ins exist (MIGRATABLE)

**Output includes Testara Flavor Score:** built-in steps / total steps × 100.

**MCP tool:** `testara_review`

---

### testara-context

Return the full Testara runtime context for the current project.

```sh
testara-agent testara-context .
```

Output: detected slices, installed modules, config coverage (which prefixes are configured), available steps/commands/validations counts, missing config blocks.

**Use before generating any artifact** to understand what's installed.

**MCP tool:** `testara_context`

---

### testara-property

Manage property keys — list, suggest, generate config blocks, explain rules.

```sh
testara-agent testara-property --mode list             # list all properties
testara-agent testara-property --mode suggest --value "http://localhost:8080"
testara-agent testara-property --mode generate --slice api --domain payment
testara-agent testara-property --mode rules            # explain properties() rules
```

**Rule:** Use `properties(key)` for ALL env-specific values. Never hardcode URLs, credentials, topic names, or DB names in feature files.

**MCP tool:** `testara_property`

---

### testara-api

Explain and generate API configuration and request specification artifacts.

```sh
testara-agent testara-api --mode explain
testara-agent testara-api --mode config --domain payment
testara-agent testara-api --mode request-spec --domain refund --flow approve-refund --write
```

**Request spec path convention:** `src/test/resources/files/{domain}/request/{flow}.json`

**Feature step:** `When [api] process request to "files/{domain}/request/{flow}"`

**When to use request spec vs direct step:**
- `GET` without params → `When [api] try GET request to "/health"`
- Any request with payload, path/query params, or reuse → request spec

**MCP tool:** `testara_api`

---

### testara-ui

Generate Testara UI artifacts — Page class, UserAction, engine config.

```sh
testara-agent testara-ui --mode explain
testara-agent testara-ui --mode page --page-name login --base-package com.company --write
testara-agent testara-ui --mode action --page-name login --action-name "login with credential" --write
testara-agent testara-ui --mode config --engine selenium
```

**Rule:** 3+ UI operations on the same page → generate `UserAction` class.

**Page URL goes in properties:**
```properties
web.page.desktop.login.url=properties(app.web.login-url)
app.web.login-url=http://localhost:3000/login
```

**UserAction usage:**
```gherkin
When user do "login with credential" in "login" page with parameter
  | username | properties(test.user.email)    |
  | password | properties(test.user.password) |
```

**MCP tool:** `testara_ui`

---

### testara-db

Explain and generate DB (SQL/Mongo) and Kafka config and feature templates.

```sh
testara-agent testara-db --slice sql --mode config --name settlement
testara-agent testara-db --slice mongo --mode feature --name product
testara-agent testara-db --slice kafka --mode config --name payment
```

All generated config uses `properties()` for host, credentials, and topic names.

**MCP tool:** `testara_db`

---

### testara-guide

Serve the embedded generation rules and guardrails.

```sh
testara-agent testara-guide             # full guide
testara-agent testara-guide properties  # properties() section only
testara-agent testara-guide ui          # UI section
```

**Call this at the start of every MCP session** before generating any artifact.

**MCP tool:** `testara_guide`

---

### knowledge

Manage the local knowledge cache.

```sh
testara-agent knowledge status          # check cache state
testara-agent knowledge refresh         # force full re-index
testara-agent knowledge clear           # delete cache
```

The cache stores the project profile in `.testara-agent/knowledge/profile-cache.json`. Cold index takes ~3s for a large mono-repo; warm cache reads in ~0.5s.

---

## Generation Rules

1. **`properties(key)` for env-specific values** — URLs, hosts, credentials, topic names, DB names, emails, test data
2. **Request spec for non-trivial API requests** — any request with payload, path/query params, or reuse
3. **`UserAction` for reusable UI flows** — 3+ operations on the same page
4. **Page URL in properties** — `web.page.desktop.{name}.url`, never in `@Page(url=...)`
5. **Include `io.github.ygrip.testara` in scan-locations** — always
6. **Generate service config before features** — `api.service.*` must exist first
7. **Compile gate** — `mvn test-compile` after `test-init --write`
8. **Step priority** — built-in Testara steps > project steps > extension artifacts > custom step (last resort)
9. **No duplicate custom steps** — command for dynamic data, validation for assertions
10. **Scan locations for new artifacts** — update `command.executor.scan-locations` when adding commands

---

## Runtime Chain

```
properties → command conversion → config binding
    → base steps → request specs → pages/actions → screenplay → compile gate
```

Testara resolves all `properties(key)` expressions at runtime from `configuration.properties`. Commands (`uuid()`, `timestamp()`, etc.) are resolved inside step parameters. Requests specs define full HTTP context as JSON. Pages/actions encapsulate UI behavior. The compile gate verifies generated code before test execution.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TESTARA_AGENT_RUN_ENABLED` | `false` | Enable `test-run --execute` and compile gate |
| `TESTARA_AGENT_WRITE_ENABLED` | `false` | Enable file writes from MCP context |
| `OPENAI_API_KEY` | — | Enable LLM-assisted generation (optional) |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Override for local/proxy LLM |
| `OPENAI_MODEL` | `gpt-4o` | Override model name |
| `JAVA_HOME` | — | Override Java path in wrapper script |

---

## Security Model

- **File writes disabled by default** in MCP mode (`TESTARA_AGENT_WRITE_ENABLED=false`)
- **Test execution disabled by default** (`TESTARA_AGENT_RUN_ENABLED=false`)
- **No secret redaction bypass** — agent will not output secrets or tokens
- **Maven command injection protection** — `test-run` validates tag expressions before passing to Maven
- All skills that write files check the target is within the project root
