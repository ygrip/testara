<p align="center">
  <img src="https://readynaz.com/content/assets/images/gallery/testara-brand-logo/01-testara-brand.webp" alt="Testara" width="600" />
</p>

# Testara

**Persistent and assurance-driven full-stack automation framework for Java.**

Testara is a modular test automation framework built on Java 21 that provides a unified API for API testing, UI automation, database verification, streaming validation, and more — all integrated with Cucumber BDD and JUnit 5.

## Features

- **API Testing** — RestAssured-based HTTP client with concurrent request support and WireMock integration
- **UI Automation** — Engine-agnostic abstraction supporting Selenium, Playwright, Appium, and Vibium with Screenplay-style interactions
- **Database Testing** — MongoDB, PostgreSQL, MariaDB, and MySQL verification
- **Streaming** — Kafka producer/consumer testing with Reactor Kafka
- **Elasticsearch** — Search index validation and querying
- **Security** — SSH/remote command execution via SSHJ
- **BDD** — Cucumber 7 step definitions with a custom ObjectFactory and scope isolation
- **JUnit 5** — Custom TestEngine with parallel execution support
- **Reporting** — Maven plugin generating HTML test reports with Thymeleaf templates
- **Spring Integration** — Optional Spring Boot auto-configuration and bean scoping
- **Command Engine** — Extensible command/expression parser with data generation (DataFaker)
- **Validation** — Rich assertion library built on AssertJ with declarative validation rules
- **Testara Agent** — AI-assisted skills for summarizing, reviewing, generating, and running tests

## Modules

| Module | Description | Docs |
|---|---|---|
| `testara-core` | Foundation: config, JSON/CSV/Excel mapping, class scanning, SPI factories | — |
| `testara-command` | Command parser and expression engine with data generators | [Documentation](testara-command/README.md) |
| `testara-validation` | Assertion and validation framework | [Documentation](testara-validation/README.md) |
| `testara-api` | REST API testing (RestAssured) | [Documentation](testara-api/README.md) |
| `testara-ui` | UI automation core (engine-agnostic) | [Documentation](testara-ui/README.md) |
| `testara-ui-selenium` | Selenium WebDriver engine | [UI Docs](testara-ui/README.md) |
| `testara-ui-playwright` | Playwright engine | [UI Docs](testara-ui/README.md) |
| `testara-ui-appium` | Appium engine for mobile testing | [UI Docs](testara-ui/README.md) |
| `testara-ui-vibium` | Vibium engine (Chromium, discovery-only locators) | [UI Docs](testara-ui/README.md) |
| `testara-database` | Database testing (MongoDB, PostgreSQL, MariaDB, MySQL) | — |
| `testara-streaming` | Kafka streaming testing | — |
| `testara-elastic` | Elasticsearch testing | — |
| `testara-security` | SSH and remote execution | — |
| `testara-cucumber` | Cucumber BDD integration | — |
| `testara-junit5` | JUnit 5 TestEngine | — |
| `testara-spring` | Spring Boot auto-configuration | — |
| `testara-properties` | External config from Consul and Vault | — |
| `testara-reporter-plugin` | Maven plugin for HTML test reports | [Documentation](testara-reporter-plugin/README.md) |
| `testara-bom` | Bill of Materials for version alignment | — |
| `testara-*-cucumber` | Pre-built Cucumber step definitions for each module | — |
| `testara-agent` | Agentic skill engine (project indexing, feature parsing, skills) | [Documentation](docs/agentic-skills.md) |
| `testara-agent-cli` | CLI for all Testara Agent skills + MCP server mode | [Documentation](docs/agentic-skills.md) |

## Testara Agent

Testara Agent is an AI-assisted CLI and MCP server that scaffolds, reviews, plans, and executes Testara automation projects. It understands the full Testara runtime — properties, commands, validations, request specs, pages, actions, and Cucumber steps.

### Install

```sh
curl -fsSL https://github.com/ygrip/testara/releases/latest/download/install.sh | bash
```

The installer downloads the agent, writes a wrapper, and **automatically configures MCP** for VS Code, Cursor, Claude Desktop, and Claude Code. Pass `--no-mcp` (or set `TESTARA_SKIP_MCP=1`) to skip MCP auto-configuration.

### 3-Step Quick Start

```sh
# 1. Scaffold a new project (interactive prompts for group ID, artifact, type)
mkdir my-tests && cd my-tests
testara-agent test-init

# 2. Generate a Testara-flavor Cucumber feature
testara-agent test-plan 'test the payment refund approval' --write

# 3. Run the tests
TESTARA_AGENT_RUN_ENABLED=true testara-agent test-run 'payment refund' --execute
```

### Skills

| Skill | Description |
|---|---|
| `test-init` | Interactive project scaffold — asks group ID, artifact, type |
| `test-plan` | Generate Testara-flavor feature with `properties()` values and request specs |
| `test-run` | Resolve intent → Cucumber tags → optional Maven execution |
| `test-command` | List / detail / generate CommandLogic classes |
| `test-validation` | List / detail / generate ValidatorLogic classes |
| `test-overview` | Project statistics |
| `test-review` | Quality review + Testara Flavor Score |
| `testara-context` | Runtime context: slices, config coverage, available steps |
| `testara-property` | Property key management and `properties()` generation |
| `testara-api` | API config and request spec generation |
| `testara-ui` | Page, UserAction, and engine config generation |
| `testara-db` | SQL / Mongo / Kafka config and feature templates |
| `testara-guide` | Embedded generation rules and guardrails |
| `knowledge` | Manage profile cache (cold: ~3s → warm: ~0.5s) |

### Generation quality

Every generated feature reports two scores:

- **Testara Flavor Score** — % of steps using Testara built-in steps (target ≥ 80%)
- **Runtime Context Score** — % of values correctly using `properties()` (target ≥ 90%)

**Guardrails** flag hardcoded URLs, credentials, and missing scan-location config before returning output.

See **[Testara Agent documentation](docs/agentic-skills.md)** for full skill reference, MCP setup, interactive init, generation rules, and security model.

## Documentation

**Start here:**

- **[Architecture](docs/ARCHITECTURE.md)** — What Testara is, when to use it, the module map, core runtime concepts, and how each slice (API/UI/DB/streaming/search) plugs in
- **[Getting Started](docs/GETTING-STARTED.md)** — Setup, first API + UI features, a cross-layer example, and the agent fast-path
- **[Best Practices](docs/BEST-PRACTICES.md)** — The approach and conventions that keep suites portable and readable

**Reference:**

- **[Testara Agent](docs/agentic-skills.md)** — All 8 agentic skills, MCP tools+prompts, knowledge store, YAML config, Docker, security model
- **[Command Engine](testara-command/README.md)** — All 50+ built-in commands, syntax reference, and how to create custom commands
- **[Validation](testara-validation/README.md)** — All 40+ validators, usage patterns, and how to create custom validations
- **[API Testing](testara-api/README.md)** — Service configuration, request building, load testing, and interceptors
- **[UI Automation](testara-ui/README.md)** — Engine setup (Selenium/Playwright/Appium), interactions, observations, page objects, and extensibility
- **[Reporter Plugin](testara-reporter-plugin/README.md)** — HTML report generation from Cucumber JSON, templates, customization, and CLI usage

## Requirements

- **Java** 21+
- **Maven** 3.9+

## Installation

Add the BOM to your project's dependency management:

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
```

Then add the modules you need:

```xml
<dependencies>
  <!-- API testing -->
  <dependency>
    <groupId>io.github.ygrip</groupId>
    <artifactId>testara-api</artifactId>
  </dependency>

  <!-- UI testing with Selenium -->
  <dependency>
    <groupId>io.github.ygrip</groupId>
    <artifactId>testara-ui-selenium</artifactId>
  </dependency>

  <!-- Cucumber BDD steps for API -->
  <dependency>
    <groupId>io.github.ygrip</groupId>
    <artifactId>testara-api-cucumber</artifactId>
  </dependency>

  <!-- JUnit 5 engine -->
  <dependency>
    <groupId>io.github.ygrip</groupId>
    <artifactId>testara-junit5</artifactId>
  </dependency>
</dependencies>
```

## Building from Source

```bash
git clone https://github.com/ygrip/testara.git
cd testara
mvn clean install
```

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Author

**Yunaz Gilang Ramadhan** — [@ygrip](https://github.com/ygrip)
