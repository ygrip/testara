# Testara

**Persistent and assurance-driven full-stack automation framework for Java.**

Testara is a modular test automation framework built on Java 21 that provides a unified API for API testing, UI automation, database verification, streaming validation, and more — all integrated with Cucumber BDD and JUnit 5.

## Features

- **API Testing** — RestAssured-based HTTP client with concurrent request support and WireMock integration
- **UI Automation** — Engine-agnostic abstraction supporting Selenium, Playwright, and Appium with Screenplay-style interactions
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

## Modules

| Module | Description |
|---|---|
| `testara-core` | Foundation: config, JSON/CSV/Excel mapping, class scanning, SPI factories |
| `testara-command` | Command parser and expression engine with data generators |
| `testara-validation` | Assertion and validation framework |
| `testara-api` | REST API testing (RestAssured) |
| `testara-ui` | UI automation core (engine-agnostic) |
| `testara-ui-selenium` | Selenium WebDriver engine |
| `testara-ui-playwright` | Playwright engine |
| `testara-ui-appium` | Appium engine for mobile testing |
| `testara-database` | Database testing (MongoDB, PostgreSQL, MariaDB, MySQL) |
| `testara-streaming` | Kafka streaming testing |
| `testara-elastic` | Elasticsearch testing |
| `testara-security` | SSH and remote execution |
| `testara-cucumber` | Cucumber BDD integration |
| `testara-junit5` | JUnit 5 TestEngine |
| `testara-spring` | Spring Boot auto-configuration |
| `testara-properties` | External config from Consul and Vault |
| `testara-reporter-plugin` | Maven plugin for HTML test reports |
| `testara-bom` | Bill of Materials for version alignment |
| `testara-*-cucumber` | Pre-built Cucumber step definitions for each module |

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

## Releasing to Maven Central

```bash
mvn clean deploy -Prelease
```

This activates the `release` profile which attaches sources, javadoc, signs artifacts with GPG, and publishes to Maven Central via the Central Publishing Plugin.

### Prerequisites for Release

1. A GPG key for signing artifacts
2. Maven `settings.xml` configured with your Central Portal credentials:

```xml
<servers>
  <server>
    <id>central</id>
    <username>YOUR_TOKEN_USERNAME</username>
    <password>YOUR_TOKEN_PASSWORD</password>
  </server>
</servers>
```

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Author

**Yunaz Gilang Ramadhan** — [@ygrip](https://github.com/ygrip)
