package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Skill: bootstrap a new Testara automation project or integrate Testara into an existing one.
 *
 * When context option "write=true", files are created on disk.
 * Otherwise returns a preview of what would be created (for MCP/AI context).
 */
public class TestInitSkill implements AgentSkill<TestInitSkill.Input, String> {

  private static final Logger LOG = Logger.getLogger(TestInitSkill.class.getName());

  public record Input(String type, String basePackage, String engine, boolean integrateExisting) {}

  @Override
  public String name() { return "test-init"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String type    = input.type() != null ? input.type() : "api";
    String basePkg = input.basePackage() != null ? input.basePackage() : "com.company.automation";
    String pkgPath = basePkg.replace('.', '/');
    boolean integrate = input.integrateExisting();
    boolean write  = "true".equals(context.options().get("write"));

    return write
        ? applyFiles(type, basePkg, pkgPath, input.engine(), integrate, context.projectRoot())
        : renderPreview(type, basePkg, pkgPath, input.engine(), integrate);
  }

  // ── Write mode ────────────────────────────────────────────────────────────

  private String applyFiles(String type, String basePkg, String pkgPath, String engine,
      boolean integrate, Path root) {
    List<String> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    try {
      // pom.xml — skip if integrating or already exists
      Path pom = root.resolve("pom.xml");
      if (integrate && Files.exists(pom)) {
        skipped.add("pom.xml (existing project — add dependencies manually, see preview)");
      } else if (!Files.exists(pom)) {
        writeFile(pom, generateFullPom(type, basePkg, engine));
        created.add("pom.xml");
      } else {
        skipped.add("pom.xml (already exists — not overwritten)");
      }

      // Directory structure
      mkdirs(root, "src/test/resources/features");
      mkdirs(root, "src/test/resources/files");
      mkdirs(root, "src/test/resources/validations");
      mkdirs(root, "src/test/java/" + pkgPath + "/runner");
      mkdirs(root, "src/test/java/" + pkgPath + "/steps");

      // configuration.properties
      writeIfAbsent(root, "src/test/resources/configuration.properties",
          generateProperties(type, basePkg), created, skipped);

      // TestRunner.java
      writeIfAbsent(root, "src/test/java/" + pkgPath + "/runner/TestRunner.java",
          generateRunner(basePkg), created, skipped);

      // StepDefinitions.java
      writeIfAbsent(root, "src/test/java/" + pkgPath + "/steps/StepDefinitions.java",
          generateStepDefinitions(basePkg), created, skipped);

      // Sample feature file
      writeIfAbsent(root, "src/test/resources/features/sample.feature",
          generateSampleFeature(type, basePkg), created, skipped);

      // UI extras
      if ("ui".equalsIgnoreCase(type) || "fullstack".equalsIgnoreCase(type)) {
        mkdirs(root, "src/test/java/" + pkgPath + "/pages");
        writeIfAbsent(root, "src/test/java/" + pkgPath + "/pages/BasePage.java",
            generatePageObject(basePkg), created, skipped);
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
    sb.append("## Next steps\n\n");
    sb.append("1. **Generate a test plan:**  \n");
    sb.append("   `testara-agent test-plan 'describe what you want to test' --write`\n\n");
    sb.append("2. **Run the tests:**  \n");
    sb.append("   `testara-agent test-run 'describe what to run' --execute`  \n");
    sb.append("   *(requires Maven and `TESTARA_AGENT_RUN_ENABLED=true`)*\n");
    return sb.toString();
  }

  private void writeIfAbsent(Path root, String relative, String content,
      List<String> created, List<String> skipped) throws IOException {
    Path target = root.resolve(relative);
    if (Files.exists(target)) {
      skipped.add(relative + " (already exists)");
    } else {
      writeFile(target, content);
      created.add(relative);
    }
  }

  private void writeFile(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }

  private void mkdirs(Path root, String relative) throws IOException {
    Files.createDirectories(root.resolve(relative));
  }

  // ── Preview mode (MCP / AI context) ──────────────────────────────────────

  private String renderPreview(String type, String basePkg, String pkgPath, String engine, boolean integrate) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Init: ").append(type.toUpperCase(Locale.ROOT)).append(" Project\n\n");
    sb.append(integrate
        ? "> **Integration mode** — only missing files will be added.\n\n"
        : "> **Bootstrap mode** — full project scaffold.\n\n");

    sb.append("## Files to Create\n\n");
    getFilesToCreate(type, pkgPath).forEach(f -> sb.append("- `").append(f).append("`\n"));
    sb.append("\n");

    sb.append("## pom.xml\n\n```xml\n");
    sb.append(generateFullPom(type, basePkg, engine));
    sb.append("```\n\n");

    sb.append("## configuration.properties\n\n```properties\n");
    sb.append(generateProperties(type, basePkg));
    sb.append("```\n\n");

    sb.append("## TestRunner.java\n\n```java\n");
    sb.append(generateRunner(basePkg));
    sb.append("```\n\n");

    sb.append("## StepDefinitions.java\n\n```java\n");
    sb.append(generateStepDefinitions(basePkg));
    sb.append("```\n\n");

    if ("ui".equalsIgnoreCase(type) || "fullstack".equalsIgnoreCase(type)) {
      sb.append("## BasePage.java\n\n```java\n");
      sb.append(generatePageObject(basePkg));
      sb.append("```\n\n");
    }

    sb.append("## sample.feature\n\n```gherkin\n");
    sb.append(generateSampleFeature(type, basePkg));
    sb.append("```\n");
    return sb.toString();
  }

  private List<String> getFilesToCreate(String type, String pkgPath) {
    List<String> files = new ArrayList<>(List.of(
        "pom.xml",
        "src/test/resources/configuration.properties",
        "src/test/resources/features/sample.feature",
        "src/test/resources/files/.gitkeep",
        "src/test/resources/validations/.gitkeep",
        "src/test/java/" + pkgPath + "/runner/TestRunner.java",
        "src/test/java/" + pkgPath + "/steps/StepDefinitions.java"));
    if ("ui".equalsIgnoreCase(type) || "fullstack".equalsIgnoreCase(type)) {
      files.add("src/test/java/" + pkgPath + "/pages/BasePage.java");
    }
    return files;
  }

  // ── File content generators ───────────────────────────────────────────────

  private String generateFullPom(String type, String basePkg, String engine) {
    String artifactId = basePkg.substring(basePkg.lastIndexOf('.') + 1) + "-automation";
    String uiDep = ("ui".equalsIgnoreCase(type) || "fullstack".equalsIgnoreCase(type))
        ? """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """
        : "";
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>

          <groupId>com.company</groupId>
          <artifactId>%s</artifactId>
          <version>1.0.0-SNAPSHOT</version>
          <packaging>jar</packaging>

          <properties>
            <maven.compiler.source>21</maven.compiler.source>
            <maven.compiler.target>21</maven.compiler.target>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <testara.version>2.0.0</testara.version>
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
                <configuration>
                  <source>21</source>
                  <target>21</target>
                </configuration>
              </plugin>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                  <includes>
                    <include>**/*Runner.java</include>
                  </includes>
                </configuration>
              </plugin>
            </plugins>
          </build>
        </project>
        """.formatted(artifactId, uiDep);
  }

  private String generateProperties(String type, String basePkg) {
    return """
        # Testara configuration
        cucumber.glue=%s.steps,io.github.ygrip.testara
        cucumber.features=src/test/resources/features
        cucumber.plugin=json:target/cucumber.json,html:target/cucumber-reports

        # Environment
        base.url=http://localhost:8080
        """.formatted(basePkg);
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

  private String generateStepDefinitions(String basePkg) {
    return """
        package %s.steps;

        import io.cucumber.java.en.*;

        /**
         * Project-specific step definitions.
         * Testara provides built-in steps for API calls, commands, and validations.
         * Add custom steps here as your test suite grows.
         */
        public class StepDefinitions {

          // Example custom step:
          // @Given("the application is running")
          // public void applicationIsRunning() {
          //   // your setup code
          // }
        }
        """.formatted(basePkg);
  }

  private String generatePageObject(String basePkg) {
    return """
        package %s.pages;

        import io.github.ygrip.testara.ui.page.PageContext;

        /**
         * Generated by Testara Agent — review before committing.
         */
        public class BasePage {

          protected final PageContext page;

          public BasePage(PageContext page) {
            this.page = page;
          }
        }
        """.formatted(basePkg);
  }

  private String generateSampleFeature(String type, String basePkg) {
    return """
        # Sample feature generated by Testara Agent.
        # Replace with your actual test scenarios.

        @sample @api @regression
        Feature: Sample API test

          @P1 @positive
          Scenario: Health check passes
            Given a valid request to get health status
            When the request is sent
            Then the response status should be 200
        """;
  }

  private String resolveUiModule(String engine) {
    return "testara-ui-cucumber";
  }
}
