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
- - - - - - - - - - - - - - nvironment-variables)
- [Security Model](#security-model)

---

## Prerequisites

| Requirement | Details |
|---|---|
| Java 21+ | Runtime for the agent JAR |
| A Testara project | Must have a `pom.xml` at the project root |
| Maven 3.9+ | Required for `/test-run` execution mode |
| (Optional) LLM API key | Enables LLM-assisted generation (OpenAI, Ollama, or compatible) |

Read-only skills (`/test-summary`, `/test-overview`, `/test-review`) require no LLM, no browser, and no external services.

---

## Installation

### Option A — Build from### Option A — Build from### O-pl t### Option A — Build from### skipTests
# Fat JAR: testara-# Fat JAR: testara-# Fat JAR: .jar
# Fat JAR: testara-# Fat JAR: testaraa -j# Fat JAR: testara-# Fat Jjar# Fat JAR: testara-# Fat JA ## Fat JAR:ra-agent 2.0.0
````````````````````````````````````````````````leases)

```bash
# Fat JAR
curl -LO https://github.com/ygrip/testara/releases/latest/download/testara-agent.jar
java -jar testara-agent.jar --vjava -jar testara-agent.jar --vjacOS/Windows)
curl -LO https://github.com/ygrip/testara/releases/latest/download/testara-agent-darwin-arm64
chmod +x testara-agent-darwin-arm64
./testara-agent-darwin-arm64 --version
```

### Option C — One-line install script

```bash
curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash
testara-agent --version
```

Windows (PowerShell):
```powershell
iwr -useb https://github.com/ygrip/testara/releases/latest/download/install.ps1 | iex
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

# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Reviie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# Revie# a-agent /test-run "run payment smoke tests"

# Re-run previously failed scenarios
testara-agent /test-run --rerun-failed

# Generate a new command class
testara-agent /test-command "generate customer id with prefix CUS and timestamp"

# Manage knowledge cache
testara-agent knowledge status
testara-agent knowledge refresh
testara-agent knowledge clear
```

---

## Skills Reference

### /test-summary

Summarizes feature files at scenario, feature, or directory level. **Read-only. No LLM required.**

```bash
testara-agent /test-summary <path> [--scenario testara-agent /test-summary <path> [--scenario testale pathtetags
- All scenario- All scenario- All scenario- io Outline)- All scenarsteps
- Background - Background - Background - step- Background - Background - Bactotal- Background - Background - Background - step- Background - Background - Bactot/features/login/login.feature
testara-agent /test-summary src/test/resources/features/payment
testara-agent /test-summary src/test/resources/features/payment/checkout.feature --scenario "Successful checkout"
```

---

### /test-overview

Statistical overview of the entire test project. **Read-only. No LLM required.**

```bash
testara-agent /test-overview [path] [--format markdown|json]
```

**What it shows:**
- Feature files, scenarios, outlines, example rows, total steps
- Step definitions, custom commands, custom validators
- Average steps per scenario, longest scenarios
- Tag distribution (sorted by usage)

```bash
testara-agent /test-overview .
testara-agent /test-overview . --format json
```

---

### /test-review

Reviews feature files for quality issues. **Read-only. No LLM required.**

```bash
testara-agent /test-review <path>
```

**Detections:**

| Severity | Finding |
|---|---|
| HIGH | Duplicate scenario names across files |
| HIGH | Scenarios with no `Then` assertion |
| MEDIUM | High-complexity scenarios (>10 steps) |
| MEDIUM | Near-duplicate step sequences (>=70% shared) |
| LOW | Scenarios with no tags |
| INFO | Shared first step -> suggest `Background` |
| INFO | Identical step patterns -> suggest `Scenario Outline` with **concrete Examples table** |

The **Scenario Outline suggestion** generates a complete, ready-to-use `Scenario Outline` block with parameterized steps and an `Examples` table extracted from the actual scenario values.

**Example:**

```gherkin
Scenario Outline: Successful login
  Given the user is on the login page
  When the user enters "<username>" and "<password>"
  Then the user should be redirected to the dashbo  Then the user should be redirected to the  |
    | ad    | ad    |n123     | ad    | ad    |n123     | ad    | ad    |n123     | ad    | ad    ral    | ag    | ad    | ad    |n123     | g expre    | ad    | tio    | ad    | ad   repo    |**Dry    | ad    | ad    |n123     | ad    |nt /test-run "<intent>" [--execute] [--dry-run] [--rerun    | ad    | ad    |n123     | ad    | ad    |n1ep    | ad    | ad    |n123     | ad    | ad    |n123 on pri    | ad    | alicit `@tags` in i    | ad    | ad    |n123     | ad    | ad    |n1i`, `ui`, `critical`, `p0`, `flaky`, `slow`)
3. OR groups (`"payment or order"`) -> `(@payment or @order)`
4. NOT clauses (`"except slow"`, `"not flaky"`) -> `not @slow`
5. Indexed project tags
6. Unresolvable -> shows available tags

**Rerun-failed mode (`--rerun-failed`):**
- Reads `target/rerun.txt` (Cucumber rer- Reads `target/rerun.txt` (Cucumk - Reads `target/rerun.txt` (Cucumber s- Reads `target/rerun.txt` (Cucumber rer- Reads `taous failures exist

**Report parsing:**
- `CucumberReportParser` extracts failed scenarios with feature URI and error messages
- Parses both `cucumber.json` and JUnit XML formats
- Structured report with pass/fail/skip counts and suggested next actions

**Execution guardrails:**
- Blocked by default — set `TESTARA_AGENT_RUN_ENABLED=true`
- 15-minute timeout
- Safe Maven command templates only (no shell injection possible)

**Examples:**

```bash
testara-agent /test-run "run payment smoke tests"
testara-agent /test-run "run api regression except slow"
testara-agent /test-run --rerun-failed
TESTARA_AGENT_RUN_ENABLED=trueTESTARA_AGENT_RUN_ENABLED=trueTESTARA_AGENT_RUN_ENABLEDecute
ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttmotttttttttttttttttttttteport json
```

---

### /test-command

GenGenGenGenGenGenGenGenGenGenGenGenGenGenG 5GenGenGenGenGenGenGenGenGenGenGenGenGenGenG 5GenGenGenGenGenGenGenGenGenGenGenGenGenGenG 5GenGenGenGenGenGenGenGenGenGenGenGenGenGenG 5GenGenGenGenGenGenGenGenGenGenGenGenGenGenG 5location config, and plaGenGenGenGenGenGenGenGenGenGenGenGenGenGenG 5GenGenGenGenGenGenGenGenGenGenGenGenGeore committing.` traceability header.

---

### /test-va### /test-va### /test-va### /test-va### /test-va### /test-va### /test-vators) or custom `ValidatorLog### /test-va### /test-va### /test-va### /test-va### /test-vati### /test-va### o|json|java] [--package <pkg>]
```

**Built-in validators:** `EQUAL`, `NOT_EQUAL`, `EM**B`, `N**Built-in validators:** `EQUAL`, `NOT_EQUAL`,S_WI**Built-in validators:** `EQUAL`, `NOT_EQUAL`, `EM**B`R_THAN`, `LESSER_THAN`, `IN_RANGE_OF`, `SORTED`, `CONTAINS_KEY`, `MATCH_SCHEMA`

---

### /test-plan

Generates Testara-compatible CuGenerates Testara-compatible CuGenerates Testara-compatible CuGenerates Testara-compatible CuGe# MISSING` markers.

```bash
tetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetet `srt/testetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetax,ttractetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetetengite setetetetetetetight|appium]
                [--integrate-existing]
```

Generates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, request spec root, validation root, rGenerates: `pom.xml`, `configuration.properties`, feature root, reque
  requireConfirmation: true
  defaultGoal: test
  timeout: 15m

llm:
  provider: openai
  model: gpt-4.1-mini
  sendSourceCode: false
  redactSecrets: true

tagAliases:
  smoke: ["@smoke", "@sanity"]
  critical: ["@P0", "@critical"]
  api: ["@api"]
  ui: ["@ui"]
  slow: ["@slow", "@performance"]
  flaky: ["@flaky"]
```

Priority: CLI flags > env vars > `testara-agent.yaml` > `configuration.properties` > built-in defaults.

---

## Knowledge Store

The agent maintains a persistent JSONL cache under `.testara-agent/knowledge/` for fast repeated invocations.

**How it works:**
1. **First run:** indexes the full project, saves fingerprints + manifest
2. **Subsequent runs:** compares file fingerprints (path + size + lastModified)
3. **No changes** -> reuses cache (sub-second startup)
4. **Feature/step files changed** -> full reindex
5. **Build config changed** (`pom.xml`, `configuration.properties`) -> full reindex
6. **Any error** -> safe fallback to direct `ProjectIndexer` (never fails)

**Cache contents:**

| File | Content |
|---|---|
| `manifest.json` | Schema version, timestamps, project hash |
| `file-fingerprints.jsonl` | Per-file path, | `file-fingerprintsfie| `file-figerprint file types:** `BUILD`, `FEATURE`, `STEP_DEFINITION`, `COMMAND`, `VALIDATI| `file-fingT_| `file-fingerprints.jsonl` | PIG`| `file-fingerprin`.gitig| `file-fingerprints.jsonl` | Per-file path, | `file-fingerprintsfie| `file-figerprint file typeols + 8 p|ompt| `filetdio JSON-RPC 2.0.

```bash
testara-agent mcp [project-root]
```

### MCP Tools

| Tool | Skill |
|---|---|
| `testara_summa| `te `/test-summa| `testara_summa| `te `/test-summa| `testara_summa| `te `/tesview` | `/tes| `testara_summa| `te `/test-summa| `testara_summa| `te `/test-summa| `testaramm| `testara_summa| `te `/test-summa| `testara_summa| `es| `testara_summa| `te `/test-summa `/test-plan` |
| `testara_init` | `/test-init` |

### MCP Prompts

| Prompt | Description |
|---|---|
| `test-summary` | Summarize tests in the specified path |
| `test-review` | Review test quali| `test-review` | `|est-plan`| `test-review` | Review test quali| `test-review` | `|est-plnd| `test-review` | Review test quali| `test-review` | `|est-plan`| `test-review` | Review test quali| `test-review` | `|est-plnd| `test-review` | Review test quali| `test-review` | `|est-plan`| `test-review` | Review test quali| `test-review` | `|est-plnd| `test-review` | Review test quali| `test-review` | `|est-plan`| `test-review` | Review test quali| `test-reviD": "| `te"
                                Co   /             ot
               son`:
```json
{
  "servers": {
    "testara": {
      "type": "stdio",
      "command": "testara-agent",
      "args": ["mcp", "."]
    }
  }
}
```

### Example prompts in AI assistants

```
Use Testara to summarize this repo's test structure.
Use Testara to review duplicated scenarios in payment features.
Use Testara to dry-run "api regression except slow tests".
Use Testara to generate a test plan for refund approval flow.
```

---

## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Confi## LLM Conct## LLM Confi## LLM Confi## LLM Confi## LLM ConfactionGuard` scrubs:
- PEM private key blocks
- Bearer/Basic auth toke- Bearer/Basic  keys (`- Bearer/Basic auth toke- Bea "...- Bearer/Basic auth t`
- `key=value` pa- ern- `key=value` pa- ern- `key=value` -ke-  p- `key=value` pa- ern- `key=value` paals
- JDBC conne- JDBC conne- JDBC conne- JDBC conne- JDBC conne- JDBC conneive - JDBC conne- JDBC conne- JDBC conne- JDBC conne- JDBC cate- JDBC conne- JDBC conne- JDBC conne- JDBC conne- JDBC connevents duplicate scenario names
- `JavaCompilationGuard` — Checks class declarations, annotations
- `SecretRedactionGuard` — Scrubs secrets before LLM upload
- `TestExecutionGuard` — Rejects shell injection in Maven commands

---

## Docker

```bash
# Build
docker build -t testara-agent .

# Run
docker run --rm -v "$PWD:/workspace" -w /workspace testara-agent /test-overview .

# MCP via Docker
docker run -i --rm -v "$PWD:/workspace" -w /workspace testara-agent mcp
```

Multi-stage build: Maven build -> `eclipse-temurin:21-jre-alpine` runtime. Non-root user.

---

## Environment Variables

| Variable | Default| Variable | Default| Variable | Default| Variable | Default| Variable | Default| Variable | Defaul` |
|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||| | |||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||| | |||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||| | ||||||||||||||`T|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||AGENT_MAX_CONTEXT_FILES` | `80` | Max files in LLM context |
| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T| `T|s |

---

## Security## Sel


# Spabi# Spabi# Spabi# Spabi# Spabi# 
|-|-|---|---|
| Read-only skills | **Enabled** | — |
| Plan-only skills | **Enabled** | �| Plan-only skite| Plan-only skills | **Enabled** | �| Plan-only skite| Plan-only skilln | **Blocked** | `TESTARA_AGENT_RUN_ENABLED=true` |
| LLM context upload | **Disabled** | `TESTARA_AGENT_API_KEY=...` |
| Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shelles a| Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shelles a| Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shelles a| Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shelles a| Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shel || Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell | Shell`.j|va` | Shell | ShelumberR| Shell | Shell | Shell | Shell json` + JUnit XML with failed scenario extraction |
| `ProjectIndexer` | Full project scanner (modules, features, steps, commands, validations, drivers, tags) |
| `IncrementalIndexer` | Fingerprint-aware reindex decisions (cache reuse vs full reindex) |
| `KnowledgeQueryService` | Structured queries over cached project knowledge |
