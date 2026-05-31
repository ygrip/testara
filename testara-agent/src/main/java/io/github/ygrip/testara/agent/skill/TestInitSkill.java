package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.validation.TestCompileGate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Skill: bootstrap a Testara project with slice-aware scaffold.
 *
 * write=true  → creates files on disk, runs mvn test-compile
 * preview     → returns markdown with all file content (MCP / AI mode)
 */
public class TestInitSkill implements AgentSkill<TestInitSkill.Input, String> {

  private static final Logger LOG = Logger.getLogger(TestInitSkill.class.getName());

  public record Input(String type, String basePackage, String engine, boolean integrateExisting,
      String groupId, String artifactId) {
    public Input(String type, String basePackage, String engine, boolean integrateExisting) {
      this(type, basePackage, engine, integrateExisting, null, null);
    }
  }

  @Override
  public String name() { return "test-init"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String type    = input.type() != null ? input.type().toLowerCase(Locale.ROOT) : "api";
    String groupId = input.groupId() != null ? input.groupId() : "io.github.ygrip";
    String artifactId = input.artifactId() != null ? input.artifactId()
        : context.projectRoot().getFileName() != null ? context.projectRoot().getFileName().toString() : "automation";
    String basePkg = input.basePackage() != null ? input.basePackage()
        : groupId + "." + artifactId.replaceAll("[^a-zA-Z0-9]+", "").toLowerCase(Locale.ROOT);
    String pkgPath = basePkg.replace('.', '/');
    boolean integrate = input.integrateExisting();
    boolean write  = "true".equals(context.options().get("write"));
    boolean compile = !"false".equals(context.options().getOrDefault("compile", "true"));

    return write
        ? applyFiles(type, basePkg, pkgPath, input.engine(), integrate, context.projectRoot(), compile)
        : renderPreview(type, basePkg, pkgPath, input.engine(), integrate);
  }

  // ── Write mode ────────────────────────────────────────────────────────────

  private String applyFiles(String type, String basePkg, String pkgPath, String engine,
      boolean integrate, Path root, boolean compile) {
    List<String> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    try {
      Path pom = root.resolve("pom.xml");
      if (integrate && Files.exists(pom)) {
        skipped.add("pom.xml (existing project — add dependencies manually, see preview)");
      } else if (!Files.exists(pom)) {
        writeFile(pom, generateFullPom(type, basePkg, engine));
        created.add("pom.xml");
      } else {
        skipped.add("pom.xml (already exists)");
      }

      mkdirs(root, "src/test/resources/features/" + type);
      mkdirs(root, "src/test/resources/files");
      mkdirs(root, "src/test/resources/validations");
      mkdirs(root, "src/test/java/" + pkgPath + "/runner");
      mkdirs(root, "src/test/java/" + pkgPath + "/steps");
      mkdirs(root, "src/test/java/" + pkgPath + "/commands");
      mkdirs(root, "src/test/java/" + pkgPath + "/validations");

      writeIfAbsent(root, "src/test/resources/configuration.properties",
          generateProperties(type, basePkg), created, skipped);
      writeIfAbsent(root, "src/test/java/" + pkgPath + "/runner/TestRunner.java",
          generateRunner(basePkg), created, skipped);
      writeIfAbsent(root, "src/test/java/" + pkgPath + "/steps/StepDefinitions.java",
          generateStepDefinitions(basePkg, type), created, skipped);
      writeIfAbsent(root, "src/test/resources/features/" + type + "/sample.feature",
          generateSampleFeature(type, basePkg), created, skipped);

      // Slice-specific extras
      boolean isUi  = type.equals("ui") || type.equals("fullstack");
      boolean isApi = type.equals("api") || type.equals("fullstack");
      if (isUi) {
        mkdirs(root, "src/test/java/" + pkgPath + "/pages");
        mkdirs(root, "src/test/java/" + pkgPath + "/actions");
        writeIfAbsent(root, "src/test/java/" + pkgPath + "/pages/BasePage.java",
            generatePageObject(basePkg), created, skipped);
      }
      if (isApi) {
        mkdirs(root, "src/test/resources/files/sample/request");
        mkdirs(root, "src/test/resources/files/sample/payload");
        writeIfAbsent(root, "src/test/resources/files/sample/request/sample-get.json",
            generateRequestSpec(basePkg, type), created, skipped);
      }
    } catch (IOException e) {
      return "Error creating project files: " + e.getMessage() + "\n";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Init: ").append(type.toUpperCase(Locale.ROOT)).append(" Project\n\n");
    sb.append("**Root:** `").append(root).append("`\n\n");
    if (!created.isEmpty()) {
      sb.append("## Created\n\n");
      created.forEach(f -> sb.append("- `").append(f).append("`\n"));
      sb.append("\n");
    }
    if (!skipped.isEmpty()) {
      sb.append("## Skipped\n\n");
      skipped.forEach(f -> sb.append("- ").append(f).append("\n"));
      sb.append("\n");
    }

    // Run compile gate
    if (compile) {
      TestCompileGate.Result result = new TestCompileGate().run(root);
      sb.append("## Compile\n\n").append(result.toLine()).append("\n\n");
    }

    sb.append("## Next steps\n\n");
    sb.append("1. Generate a test plan:  \n");
    sb.append("   `testara-agent test-plan 'describe what to test' --write`\n\n");
    sb.append("2. Run the tests:  \n");
    sb.append("   `TESTARA_AGENT_RUN_ENABLED=true testara-agent test-run 'describe' --execute`\n");
    return sb.toString();
  }

  private void writeIfAbsent(Path root, String rel, String content,
      List<String> created, List<String> skipped) throws IOException {
    Path target = root.resolve(rel);
    if (Files.exists(target)) { skipped.add(rel + " (exists)"); return; }
    writeFile(target, content);
    created.add(rel);
  }

  private void writeFile(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }

  private void mkdirs(Path root, String rel) throws IOException {
    Files.createDirectories(root.resolve(rel));
  }

  // ── Preview mode ──────────────────────────────────────────────────────────

  private String renderPreview(String type, String basePkg, String pkgPath, String engine, boolean integrate) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Init: ").append(type.toUpperCase(Locale.ROOT)).append(" Project\n\n");
    sb.append(integrate ? "> Integration mode\n\n" : "> Bootstrap mode\n\n");

    sb.append("## pom.xml\n\n```xml\n").append(generateFullPom(type, basePkg, engine)).append("```\n\n");
    sb.append("## configuration.properties\n\n```properties\n").append(generateProperties(type, basePkg)).append("```\n\n");
    sb.append("## TestRunner.java\n\n```java\n").append(generateRunner(basePkg)).append("```\n\n");
    sb.append("## sample.feature\n\n```gherkin\n").append(generateSampleFeature(type, basePkg)).append("```\n");
    return sb.toString();
  }

  // ── File content generators ───────────────────────────────────────────────

  private String generateFullPom(String type, String basePkg, String engine) {
    // Try to extract groupId from basePkg (everything before the last segment)
    int lastDot = basePkg.lastIndexOf('.');
    String pomGroupId = lastDot > 0 ? basePkg.substring(0, lastDot) : basePkg;
    String artifactId = lastDot > 0 ? basePkg.substring(lastDot + 1) + "-automation" : basePkg + "-automation";
    String sliceDep = switch (type) {
      case "ui", "fullstack" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      case "database-sql", "sql" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-database-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      default -> "";
    };
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>

          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>1.0.0-SNAPSHOT</version>

          <properties>
            <maven.compiler.source>21</maven.compiler.source>
            <maven.compiler.target>21</maven.compiler.target>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <testara.version>2.0.1</testara.version>
          </properties>

          <dependencies>
            <dependency>
              <groupId>io.github.ygrip</groupId>
              <artifactId>testara-api-cucumber</artifactId>
              <version>${testara.version}</version>
              <scope>test</scope>
            </dependency>
        %s
            <dependency>
              <groupId>org.junit.platform</groupId>
              <artifactId>junit-platform-suite</artifactId>
              <version>1.10.2</version>
              <scope>test</scope>
            </dependency>
          </dependencies>

          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
              </plugin>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                  <includes><include>**/*Runner.java</include></includes>
                </configuration>
              </plugin>
            </plugins>
          </build>
        </project>
        """.formatted(pomGroupId, artifactId, sliceDep);
  }

  private String generateProperties(String type, String basePkg) {
    String glue = basePkg + ".steps,io.github.ygrip.testara";
    String base = """
        cucumber.glue=%s
        cucumber.features=src/test/resources/features
        cucumber.plugin=json:target/cucumber.json,html:target/cucumber-reports

        command.executor.scan-locations=io.github.ygrip.testara,%s.commands
        validator.helper.scan-locations=io.github.ygrip.testara,%s.validations
        """.formatted(glue, basePkg, basePkg);

    return base + switch (type) {
      case "api" -> """
          # API service configuration
          api.service.sample-api.host=http://localhost:8080
          api.service.sample-api.basePath=/api/v1
          api.service.sample-api.default_specification=sample-api
          spec.api.sample-api.header.Content-Type=application/json
          spec.api.sample-api.header.Accept=application/json
          """;
      case "ui", "fullstack" -> """
          # UI engine configuration
          automation.engine.default-engine=selenium
          automation.engine.active-engines=selenium
          selenium.driver.owner=testara
          selenium.driver.headless=true
          selenium.driver.page-scan-locations=io.github.ygrip.testara,%s.pages
          selenium.driver.action-scan-locations=io.github.ygrip.testara,%s.actions
          web.page.desktop.home.url=http://localhost:3000
          """.formatted(basePkg, basePkg);
      case "sql", "database-sql" -> """
          # Database configuration
          sql.datasource.settlementDb.url=jdbc:postgresql://localhost:5432/testdb
          sql.datasource.settlementDb.username=testuser
          sql.datasource.settlementDb.password=testpass
          sql.datasource.settlementDb.driver=org.postgresql.Driver
          """;
      case "mongo", "database-mongo" -> """
          # MongoDB configuration
          mongo.datasource.productDb.uri=mongodb://localhost:27017/testdb
          """;
      default -> "";
    };
  }

  private String generateRunner(String basePkg) {
    return """
        package %s.runner;

        import io.cucumber.junit.platform.engine.Constants;
        import org.junit.platform.suite.api.*;

        @Suite
        @IncludeEngines("cucumber")
        @ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME,
            value = "%s.steps,io.github.ygrip.testara")
        @ConfigurationParameter(key = Constants.FEATURES_PROPERTY_NAME,
            value = "src/test/resources/features")
        @ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
            value = "json:target/cucumber.json,html:target/cucumber-reports")
        public class TestRunner {}
        """.formatted(basePkg, basePkg);
  }

  private String generateStepDefinitions(String basePkg, String type) {
    return """
        package %s.steps;

        import io.cucumber.java.en.*;

        /**
         * Project-specific step definitions.
         * Testara provides built-in steps via io.github.ygrip.testara glue:
         *   - API: [api] using service, [api] process request, [api] response statusCode...
         *   - UI:  user using web in desktop, user click the "element"...
         *   - SQL: [sql] connect to database, [sql] execute database query...
         *
         * Add custom steps here only for domain-specific business logic
         * not expressible through Testara built-in steps, commands, or validations.
         */
        public class StepDefinitions {

          // Example — add only when no Testara built-in step covers the behavior:
          // @Given("the {string} service is configured with {string} profile")
          // public void serviceConfiguredWithProfile(String service, String profile) {
          //     // custom setup
          // }
        }
        """.formatted(basePkg);
  }

  private String generatePageObject(String basePkg) {
    return """
        package %s.pages;

        import io.github.ygrip.testara.ui.page.PageContext;

        public class BasePage {
          protected final PageContext page;
          public BasePage(PageContext page) { this.page = page; }
        }
        """.formatted(basePkg);
  }

  private String generateRequestSpec(String basePkg, String type) {
    return """
        {
          "specification": "sample-api",
          "httpMethod": "GET",
          "url": "/sample/{id}",
          "contentType": "application/json",
          "pathParameters": {
            "id": "uuid()"
          }
        }
        """;
  }

  private String generateSampleFeature(String type, String basePkg) {
    return switch (type) {
      case "api" -> """
          # Sample API feature — uses Testara built-in ApiBaseSteps.
          # Replace with your actual domain flow.

          @api @sample @regression
          Feature: Sample API test

            Background:
              Given [api] using service with alias sample-api

            @P1 @positive
            Scenario: Get resource successfully
              Given [api] prepare pathParam for id with value "uuid()"
              When [api] process request to "sample/request/sample-get"
              Then [api] response statusCode should be 200
              Then [api] assign previous response data to sampleResponse

            @P2 @negative
            Scenario: Get resource with invalid id returns 404
              Given [api] prepare pathParam for id with value "invalid-id"
              When [api] process request to "sample/request/sample-get"
              Then [api] response statusCode should be 404
              Then [api] response success should be false
          """;
      case "ui", "fullstack" -> """
          # Sample UI feature — uses Testara built-in UIBaseSteps.
          # Replace with your actual page flow.

          @ui @sample @regression
          Feature: Sample UI test

            Background:
              Given user using web in desktop

            @P1 @positive
            Scenario: User navigates to home page
              When user open "home" page
              Then user is in "home" page

            @P2 @negative
            Scenario: User sees error on invalid input
              When user open "home" page
              When user enter value "invalid" on "searchInput"
              When user click the "searchButton"
              Then user should see "errorMessage" is displayed
          """;
      case "sql", "database-sql" -> """
          # Sample SQL feature — uses Testara built-in SqlBaseSteps.

          @database @sql @sample @regression
          Feature: Sample database validation

            @P1
            Scenario: Record exists in database
              Given [sql] connect to database with name settlementDb
              Given [sql] prepare query with value "select * from sample where id = 'uuid()'"
              When [sql] execute database query
              Then [sql] assign previous database response to sampleRows
          """;
      case "mongo", "database-mongo" -> """
          # Sample Mongo feature — uses Testara built-in MongoBaseSteps.

          @database @mongo @sample @regression
          Feature: Sample MongoDB validation

            @P1
            Scenario: Document exists in collection
              Given [mongo] connect to database with name productDb
              Given [mongo] select collection with name sample
              When [mongo] select data with query :
                | query | {"_id": "uuid()"} |
                | limit | 1                 |
              Then [mongo] assign previous database response to sampleRows
          """;
      default -> """
          # Sample feature — replace with your actual scenario.

          @sample @regression
          Feature: Sample test

            @P1 @positive
            Scenario: Happy path
              Given the system is in a valid state # MISSING
              When the operation is performed      # MISSING
              Then the result should be successful  # MISSING
          """;
    };
  }
}
