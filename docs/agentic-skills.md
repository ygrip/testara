<p align="center">
  <img src="https://readynaz.com/content/assets/images/gallery/testara-brand-logo/01-testara-brand.webp" alt="Testara" width="600" />
</p>

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
    - [Async test runs (MCP)](#async-test-runs-mcp)
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
4. **Automatically configures MCP** for VS Code, Cursor, Claude Desktop, and Claude Code (optional — pass `--no-mcp` or set `TESTARA_SKIP_MCP=1` to skip)

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

# 3. Execute the tests (test execution is enabled by default)
testara-agent test-run 'login flow' --execute
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

Test execution and file writes are **enabled by default** — no env var is required to run tests or
write files from MCP tools. Set `TESTARA_AGENT_RUN_ENABLED=false` and/or `TESTARA_AGENT_WRITE_ENABLED=false`
in `env` below only if you want to hard-disable one of them for this server instance.

**VS Code / Cursor** — add to `~/Library/Application Support/Code/User/mcp.json`:

```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "/Users/<you>/.local/bin/testara-agent",
      "args": ["mcp"],
      "env": {
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
      "args": ["mcp"]
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
6. testara_run            → start tests (returns run_started: <runId>)
7. testara_run_status     → poll with waitSeconds=30 until the state is final, then report
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
testara-agent test-init --examples              # include demo sample artifacts
testara-agent test-init --preview                # show files without writing
```

MCP/agent usage is deliberately interactive for Maven coordinates: if `groupId`
or `artifactId` is omitted, `testara_init` returns `needs_input:
testara_init_coordinates`. The agent must ask the user for manual coordinates,
or call again with `autoGenerateCoordinates=true` when the user chooses the
generated defaults.

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
src/test/java/{pkg}/Junit5RunnerTests.java
src/test/java/{pkg}/Junit4RunnerTests.java
src/main/java/{pkg}/command/
src/main/java/{pkg}/validation/
```

By default `test-init` creates only bootstrap infrastructure. It does not create
generic `StepDefinitions`, `HomePage`, sample feature files, sample request
specs, or sample service aliases. Use `testara_ui`, `testara_api`, and
`testara_plan` to generate contextual artifacts, or pass `--examples` /
`includeExamples=true` when a demo scaffold is explicitly desired.

After writing, runs `mvn test-compile` and reports the result. Generated POMs
use Failsafe, so executable automation runs use `mvn verify`. Generated
`junit-platform.properties` defaults to serial execution and no retries to avoid
duplicate engine/retry-state issues during bootstrap.

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
- `${ENV:fallback}` in property files; `properties(key)` only in features/request specs for application values
- Request spec path (`files/{domain}/request/{flow}`) instead of inline params
- **Testara Flavor Score** — % of steps using built-in Testara steps
- **Runtime Context Score** — % of generated values correctly using `properties()`
- **Guardrail warnings** if hardcoded URLs or credentials are detected

If the request is underspecified, `testara_plan` returns `needs_input` instead
of guessing. Agents should ask the user for the missing slice-specific context:
API method/service/path/expected validation, UI page/action/expected state and
selectors, or DB/Kafka/Elastic alias/query/topic/index details.

**`--write` flag:** writes the `.feature` file and, idempotently, the request specs, service config
(`api.service.*` in `configuration.properties`, including
`automation.config.script-folder=/src/test/resources/`), and application values the plan references.
**`--overwrite`** replaces files that already exist (default false — an existing file is reported as
`exists: <path> (pass overwrite=true to replace)` and left untouched). **`--compile`** runs the
`mvn test-compile` gate after writing.

The write is blocked — nothing is written — when a generated step does not link to a real step
definition or built-in Testara step: `write blocked: <n> step(s) do not link to a step definition
and <n> step(s) are MISSING — nothing was written`.

**MCP tool:** `testara_plan` (options: `createFiles`, `overwrite`, `compile`, `slice`, `domain`)

---

### test-run

Resolve natural language test intent to a Cucumber tag expression and optionally execute. Test
execution is **enabled by default** — set `TESTARA_AGENT_RUN_ENABLED=false` to disable it.

```sh
testara-agent test-run 'run payment smoke tests'                       # dry-run (default; no --execute)
testara-agent test-run 'all regression tests' --execute                # execute
testara-agent test-run 'rerun failed' --rerun-failed --execute         # rerun from rerun.txt
testara-agent test-run 'run @smoke except @slow or @flaky' --execute   # → @smoke and not (@slow or @flaky)
testara-agent test-run 'run @smoke' --module modules/api --execute     # restrict to a module
testara-agent test-run 'run @smoke' --execute --timeout-minutes 30
testara-agent test-run 'run @smoke' --execute --report json
```

`--dry-run` (optionally `--dry-run=false`) always wins over `--execute`; without `--execute`, only the
plan is shown. `--timeout-minutes` defaults to 15 and kills the whole build process tree on timeout.
`--module` accepts a relative path (`modules/api`) or `:artifactId`. `--report` controls the format of
an *executed* run's output (`markdown` default, or `json`).

**Gradle projects** (detected from `build.gradle`/`build.gradle.kts`) are supported alongside Maven:
Cucumber tag/rerun filters are passed as `-Pcucumber.*` project properties through an agent-owned,
regenerated-per-run init script (`.testara-agent/gradle/testara-cucumber.init.gradle`), and the test
task defaults to `test` (override with `--gradle-task`, Gradle only). Maven runs use `mvnw`/`mvn`
(`.cmd` on Windows); the whole process tree is killed on timeout or interruption.

`testara_run` is context-driven. Exact `@tags` win, project-indexed tag words
map to tags, and feature/scenario text maps to the matching scenario's feature
and scenario tags. Multiple resolved tags are joined with `and` by default;
`or` is used only when the user explicitly asks for an OR run. If the MCP server
was launched outside the project, pass `projectRoot` so the tool can index the
features before resolving natural language.

**Verdict:** `FAILED` when the build exits non-zero or the parsed report has failures, `TIMEOUT` when
the timeout is hit, `CANCELLED` when an MCP run is cancelled (both always win), otherwise `PASSED`. Only a `cucumber.json`/JUnit XML report written
*by this run* is used — an older report on disk is ignored and the result shows `report missing`.
Before/after hooks, background steps, and ambiguous/undefined/pending steps all fail the scenario.

**Exit code** (CLI): `0` passed or plan-only; `1` failed, timed out, cancelled, or preflight matched 0 scenarios;
`2` invalid/missing input or execution blocked (e.g. `TESTARA_AGENT_RUN_ENABLED=false`).

**MCP tool:** `testara_run` (options: `dryRun`, `execute` — default `true` for this tool — `rerunFailed`,
`module`, `timeoutMinutes`, `gradleTask`, `format`, `wait`)

#### Async test runs (MCP)

A build can take longer than an MCP client waits for one call, so an executed `testara_run` does not
block: it launches the build and returns at once. Plans, dry runs, preflight failures and blocked
runs still answer synchronously.

| Tool | Arguments | Returns |
|---|---|---|
| `testara_run` | `input`*, run options above, `wait` (default `false`) | `run_started: <runId>` with `state: RUNNING`, the command and log path; `wait=true` blocks and returns the final result |
| `testara_run_status` | `runId`*, `waitSeconds` (0–60, default 0), `format` (`markdown`/`json`), `projectRoot` | `RUNNING` with elapsed time and the last 30 log lines, or `PASSED`/`FAILED`/`TIMEOUT`/`CANCELLED` with the full final result |
| `testara_run_cancel` | `runId`*, `format`, `projectRoot` | kills the build process tree and returns the `CANCELLED` state; a finished run returns its final state |

Flow:

1. `testara_run {"input":"@smoke"}` → `run_started: <runId>`.
2. `testara_run_status {"runId":"<runId>","waitSeconds":30}` → repeat while the state is `RUNNING`
   (each call waits up to 30 s, so a client never polls tightly).
3. Report the final result returned once the state is `PASSED`, `FAILED`, `TIMEOUT` or `CANCELLED`.
4. `testara_run_cancel {"runId":"<runId>"}` stops a run early.

Only one run is active per project root: a second `testara_run` answers
`run_in_progress: <runId>` (not an error) until the first one finishes. An unknown `runId` is an
`isError` result `unknown_run: <runId>`. Each run's metadata (`runId`, `command`, `logFile`,
`startedAt`, `state`, `finishedAt`, `exitCode`) is written to `.testara-agent/runs/<runId>.json` on
start and completion, so `testara_run_status` still reports the recorded state and log path after a
server restart. The server keeps the last 20 finished runs in memory, answers `ping` and `tools/list`
while tool calls run on a small worker pool, cancels a `wait=true` run (or ends a status long-poll)
when the client sends `notifications/cancelled`, and cancels every active run when stdin closes or
the JVM shuts down. `CANCELLED` counts as a failure (CLI exit code `1`); the CLI `test-run` stays
synchronous.

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
| `properties(key)` | String | Read application/config value at runtime |
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

**Rule:** property files should use `${ENV:fallback}` and should not point at another key with `properties(...)`. Use `properties(key)` in feature files/request specs when the key lives in `application.properties`.

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
web.page.desktop.login.url=${APP_WEB_LOGIN_URL:http://localhost:3000/login}
```

**UserAction usage:**
```java
@OnPage(value = {LoginPage.class})
public class LoginActions extends UserAction {
  @Action("login with credential")
  public void loginWithCredential(Map<String, Object> params) {
    attemptsTo(
        Enter.text(String.valueOf(params.get("username"))).into("username field"),
        Enter.text(String.valueOf(params.get("password"))).into("password field"),
        Click.on("button login")
    );
  }
}
```

```gherkin
When user do "login with credential" in "login" page with parameter
  |key|value|
  | username | properties(test.user.username) |
  | password | properties(test.user.password) |
```

**MCP tool:** `testara_ui`

---

### testara-db

Explain and generate SQL, Mongo, Elasticsearch, or Kafka config and feature templates.

```sh
testara-agent testara-db --slice sql --mode config --name settlement
testara-agent testara-db --slice mongo --mode feature --name product
testara-agent testara-db --slice kafka --mode config --name payment
testara-agent testara-db --slice elastic --mode config --name catalog
```

Generated config uses `${ENV:fallback}` for host, credential, and topic values. Feature/request data can still use `properties(key)` for application values.

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

1. **Property value split** — `configuration.properties` for framework wiring, `application.properties` for user values, `${ENV:fallback}` in property files, `properties(key)` only in features/specs
2. **Request spec for non-trivial API requests** — any request with payload, path/query params, or reuse
3. **`UserAction` for reusable UI flows** — top-level `@OnPage` classes only; use `allowAnonymousCall = true` for intentional cross-page actions
4. **Page URL in properties** — `web.page.desktop.{name}.url`, never in `@Page(url=...)`
5. **Include `io.github.ygrip.testara` in scan-locations** — always
6. **Generate service config before features** — `api.service.*` must exist first
7. **Compile gate** — `mvn test-compile` runs automatically after `test-init` (unless `--preview`), and after `test-plan`/`testara-ui`/`testara_bootstrap` when `--compile`/`compile=true` is passed
8. **Step priority** — built-in Testara steps > project steps > extension artifacts > custom step (last resort)
9. **No duplicate custom steps** — command for dynamic data, validation for assertions
10. **Clean init by default** — no placeholder `StepDefinitions`, `HomePage`, sample features/specs, or fake service aliases unless examples are requested
11. **Scenario recording off by default** — generated UI configs use `automation.engine.screenshot-output-type=IMAGE`; switch to `VIDEO` only when requested
12. **Scan locations for new artifacts** — update `command.executor.scan-locations` when adding commands

---

## Runtime Chain

```
properties → command conversion → config binding
    → base steps → request specs → pages/actions → screenplay → compile gate
```

Testara resolves `properties(key)` expressions at runtime from loaded property sources such as `application.properties` and `configuration.properties`. Commands (`uuid()`, `timestamp()`, etc.) are resolved inside step parameters. Request specs define full HTTP context as JSON. Pages/actions encapsulate UI behavior. The compile gate verifies generated code before test execution.

---

## Environment Variables

Test execution and file writes are **enabled by default**; the two `_ENABLED` switches below only
ever turn a capability *off* — no per-call argument, CLI flag, or checked-in `testara-agent.yaml` can
turn them back on.

| Variable | Default | Description |
|---|---|---|
| `TESTARA_AGENT_RUN_ENABLED` | enabled (`false` disables) | Set to `false` to disable `test-run --execute` and the compile gate |
| `TESTARA_AGENT_WRITE_ENABLED` | enabled (`false` disables) | Set to `false` to hard-disable file writes for every MCP/CLI call |
| `TESTARA_AGENT_PROVIDER` | `openai` | LLM provider: `openai`, or `local`/`ollama` for a local model server |
| `TESTARA_AGENT_MODEL` | `gpt-4.1-mini` (`llama3.1` for `local`/`ollama`) | LLM model name |
| `TESTARA_AGENT_API_KEY` | — | API key for the OpenAI-compatible provider |
| `TESTARA_AGENT_BASE_URL` | `https://api.openai.com/v1` (`http://localhost:11434` for `local`/`ollama`) | LLM endpoint override |
| `TESTARA_AGENT_TEMPERATURE` | `0.2` | LLM sampling temperature |
| `TESTARA_AGENT_MAX_CONTEXT_FILES` | `80` | Max files fed to the LLM as context |
| `TESTARA_AGENT_MAX_OUTPUT_FILES` | `20` | Max files the LLM may produce in one call |
| `TESTARA_AGENT_APPLY_ENABLED` | `false` | Reserved for LLM-driven writes |
| `JAVA_HOME` | — | Override Java path in wrapper script |

LLM configuration is currently **config-only** — no skill calls out to an LLM yet, so these variables
have no effect on generation today beyond being readable via `LlmConfig`. When an API key comes from
`TESTARA_AGENT_API_KEY`, `llm.baseUrl` from a checked-in `testara-agent.yaml` is ignored and only
`TESTARA_AGENT_BASE_URL` (or the provider default) can set the endpoint, so a key from the environment
is never sent to a host chosen by repository config.

---

## Security Model

- **File writes and test execution are enabled by default.** `TESTARA_AGENT_WRITE_ENABLED=false` (env)
  or `write: { enabled: false }` in the project's `testara-agent.yaml` hard-disables writes for every
  call; `TESTARA_AGENT_RUN_ENABLED=false` disables `test-run --execute` and the compile gate. Neither a
  per-call `write`/`overwrite`/`createFiles` argument nor a CLI flag can re-enable a switch that is off.
- **A checked-in `testara-agent.yaml` can only ever disable writes, never enable them.** `write`,
  `overwrite`, and `createFiles` are call-only keys; if present in the YAML they are ignored (with a
  warning) rather than applied.
- **Agent modes** — `READ_ONLY` (analyze only), `PLAN` (plan/dry-run, no writes), `APPLY` (writes files
  or executes commands). A tool call only reaches `APPLY` from an explicit `write`/`createFiles`
  argument (or CLI `--write`) or from an executed `test-run`.
- **Secret redaction** — `SecretRedactionGuard` keeps property values and secrets out of tool output.
- **Maven/Gradle command injection protection** — builds run via `ProcessBuilder` argv (no shell);
  `TestExecutionGuard` validates the executable (`mvn`/`mvnw`/`gradle`/`gradlew` and their Windows
  `.cmd`/`.bat` forms), the tag expression, and the module/task name before executing.
- **Path containment** — `ProjectPathGuard` keeps every generated file target within the project root.
- **LLM key host pinning** — an API key from the environment is never sent to an endpoint chosen by a
  checked-in YAML file; see [Environment Variables](#environment-variables).
