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
 * write=true  -> creates files on disk, runs mvn test-compile
 * preview     -> returns markdown with all file content (MCP / AI mode)
 */
public class TestInitSkill implements AgentSkill<TestInitSkill.Input, String> {

  private static final Logger LOG = Logger.getLogger(TestInitSkill.class.getName());
  private final TestaraVersionResolver versionResolver;

  public TestInitSkill() {
    this(new TestaraVersionResolver());
  }

  TestInitSkill(TestaraVersionResolver versionResolver) {
    this.versionResolver = versionResolver;
  }

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
    boolean isUiType = type.equals("ui") || type.equals("fullstack");
    boolean autoCoordinates = "true".equals(context.options().get("autoGenerateCoordinates"));
    // Engine prompt: only for ui/fullstack, only when engine not specified
    if (isUiType && input.engine() == null && !"true".equals(context.options().get("engineConfirmed"))) {
      return enginePrompt(type);
    }
    if (!autoCoordinates && (input.groupId() == null || input.artifactId() == null)) {
      return coordinatePrompt(context.projectRoot(), type, input);
    }
    String groupId = input.groupId() != null ? input.groupId() : "io.github.ygrip";
    String artifactId = input.artifactId() != null ? input.artifactId()
        : context.projectRoot().getFileName() != null ? toKebab(context.projectRoot().getFileName().toString()) : "automation";
    String basePkg = input.basePackage() != null ? input.basePackage()
        : groupId + "." + artifactId.replaceAll("[^a-zA-Z0-9]+", "").toLowerCase(Locale.ROOT);
    String pkgPath = basePkg.replace('.', '/');
    boolean integrate = input.integrateExisting();
    boolean write  = "true".equals(context.options().get("write"));
    boolean compile = !"false".equals(context.options().getOrDefault("compile", "true"));
    boolean includeExamples = "true".equals(context.options().get("includeExamples"));
    if (write && isUnsafeImplicitRoot(context)) {
      return """
          needs_input: testara_init_project_root
          question: testara_init is about to write files, but the MCP server project root is not a safe workspace target.
          currentRoot: %s
          action: call testara_init again with projectRoot set to the intended workspace directory.
          note: refusing to scaffold into user home or filesystem root.
          """.formatted(context.projectRoot());
    }

    return write
        ? applyFiles(type, groupId, artifactId, basePkg, pkgPath, input.engine(), integrate, context.projectRoot(),
            compile, includeExamples)
        : renderPreview(type, groupId, artifactId, basePkg, pkgPath, input.engine(), integrate, context.projectRoot(),
            includeExamples);
  }

  private boolean isUnsafeImplicitRoot(AgentContext context) {
    if ("true".equals(context.options().get("projectRootExplicit"))) return false;
    Path root = context.projectRoot().toAbsolutePath().normalize();
    Path home = Path.of(System.getProperty("user.home", "")).toAbsolutePath().normalize();
    return root.equals(home) || root.getParent() == null;
  }

  private String coordinatePrompt(Path root, String type, Input input) {
    String defaultArtifact = root.getFileName() != null ? toKebab(root.getFileName().toString()) : "automation";
    return """
        needs_input: testara_init_coordinates
        question: Ask the user for their Maven groupId and artifactId before proceeding.
        missing: %s
        instruction: Do NOT guess or auto-generate these values. Ask the user directly.
        example_groupId: com.example.qa (their company/project Maven group)
        example_artifactId: %s (the project directory name is a common choice)
        next_step: call testara_init again with the user-provided groupId and artifactId values.
        current: type=%s, basePackage=%s, engine=%s
        """.formatted(missingCoordinates(input), defaultArtifact, type,
        input.basePackage() == null ? "<auto from coordinates>" : input.basePackage(),
        input.engine() == null ? "selenium" : input.engine());
  }

  private String enginePrompt(String type) {
    return """
        needs_input: testara_init_engine
        question: Ask the user which browser automation engine to use for this %s project.
        options:
          selenium (default): mature, wide browser/grid support, requires WebDriver binaries
          playwright: faster startup, auto-manages browsers, headless-first
          appium: mobile automation (Android/iOS)
        instruction: Do NOT default silently. Ask the user to confirm or choose an engine.
        next_step: call testara_init again with the chosen engine (engine=selenium|playwright|appium).
        """.formatted(type);
  }

  private String missingCoordinates(Input input) {
    if (input.groupId() == null && input.artifactId() == null) return "groupId, artifactId";
    return input.groupId() == null ? "groupId" : "artifactId";
  }

  // ── Write mode ────────────────────────────────────────────────────────────

  private String applyFiles(String type, String groupId, String artifactId, String basePkg, String pkgPath, String engine,
      boolean integrate, Path root, boolean compile, boolean includeExamples) {
    List<String> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    try {
      Path pom = root.resolve("pom.xml");
      if (integrate && Files.exists(pom)) {
        skipped.add("pom.xml (existing project — add dependencies manually, see preview)");
      } else if (!Files.exists(pom)) {
        writeFile(pom, generateFullPom(type, groupId, artifactId, basePkg, engine, root));
        created.add("pom.xml");
      } else {
        skipped.add("pom.xml (already exists)");
      }

      mkdirs(root, "src/test/resources/features/" + type);
      mkdirs(root, "src/test/resources/files");
      mkdirs(root, "src/test/resources/validations");
      mkdirs(root, "src/main/java/" + pkgPath + "/command");
      mkdirs(root, "src/main/java/" + pkgPath + "/validation");

      writeIfAbsent(root, "src/test/resources/configuration.properties",
          generateProperties(type, basePkg, engine, includeExamples), created, skipped);
      writeIfAbsent(root, "src/test/resources/cucumber.properties",
          generateCucumberProperties(type, basePkg, includeExamples), created, skipped);
      writeIfAbsent(root, "src/test/resources/junit-platform.properties",
          generateJunitPlatformProperties(), created, skipped);
      writeIfAbsent(root, "src/test/resources/application.properties",
          generateApplicationProperties(type, includeExamples), created, skipped);
      writeIfAbsent(root, "src/test/resources/log4j2.xml",
          generateLog4j2Config(basePkg), created, skipped);
      writeIfAbsent(root, "src/test/java/" + pkgPath + "/Junit5RunnerTests.java",
          generateJunit5Runner(basePkg), created, skipped);
      writeIfAbsent(root, "src/test/java/" + pkgPath + "/Junit4RunnerTests.java",
          generateJunit4Runner(basePkg), created, skipped);
      if (includeExamples) {
        writeIfAbsent(root, "src/test/resources/features/" + type + "/sample.feature",
            generateSampleFeature(type, basePkg), created, skipped);
      }

      // Slice-specific extras
      boolean isUi  = type.equals("ui") || type.equals("fullstack");
      boolean isApi = type.equals("api") || type.equals("fullstack");
      if (isUi) {
        mkdirs(root, "src/main/java/" + pkgPath + "/page");
        mkdirs(root, "src/main/java/" + pkgPath + "/action");
        if (includeExamples) {
          writeIfAbsent(root, "src/main/java/" + pkgPath + "/page/HomePage.java",
              generatePageObject(basePkg, engine), created, skipped);
        }
      }
      if (isApi) {
        if (includeExamples) {
          mkdirs(root, "src/test/resources/files/sample/request");
          mkdirs(root, "src/test/resources/files/sample/payload");
          writeIfAbsent(root, "src/test/resources/files/sample/request/sample-get.json",
              generateRequestSpec(basePkg, type), created, skipped);
        }
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

  private String renderPreview(String type, String groupId, String artifactId, String basePkg, String pkgPath,
      String engine, boolean integrate, Path root, boolean includeExamples) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Init: ").append(type.toUpperCase(Locale.ROOT)).append(" Project\n\n");
    sb.append(integrate ? "> Integration mode\n\n" : "> Bootstrap mode\n\n");

    sb.append("## pom.xml\n\n```xml\n").append(generateFullPom(type, groupId, artifactId, basePkg, engine, root)).append("```\n\n");
    sb.append("## log4j2.xml\n\n```xml\n").append(generateLog4j2Config(basePkg)).append("```\n\n");
    sb.append("## configuration.properties\n\n```properties\n").append(generateProperties(type, basePkg, engine, includeExamples)).append("```\n\n");
    sb.append("## cucumber.properties\n\n```properties\n").append(generateCucumberProperties(type, basePkg, includeExamples)).append("```\n\n");
    sb.append("## junit-platform.properties\n\n```properties\n").append(generateJunitPlatformProperties()).append("```\n\n");
    sb.append("## application.properties\n\n```properties\n").append(generateApplicationProperties(type, includeExamples)).append("```\n\n");
    sb.append("## Junit5RunnerTests.java\n\n```java\n").append(generateJunit5Runner(basePkg)).append("```\n\n");
    sb.append("## Junit4RunnerTests.java\n\n```java\n").append(generateJunit4Runner(basePkg)).append("```\n");
    if (includeExamples) {
      sb.append("\n## sample.feature\n\n```gherkin\n").append(generateSampleFeature(type, basePkg)).append("```\n");
    }
    if (includeExamples && (type.equals("ui") || type.equals("fullstack"))) {
      sb.append("\n## HomePage.java\n\n```java\n").append(generatePageObject(basePkg, engine)).append("```\n");
    }
    if (includeExamples && (type.equals("api") || type.equals("fullstack"))) {
      sb.append("\n## sample-get.json\n\n```json\n").append(generateRequestSpec(basePkg, type)).append("```\n");
    }
    if (!includeExamples) {
      sb.append("\n> Example pages, features, and request specs are omitted by default. ");
      sb.append("Generate contextual artifacts with `testara_ui`, `testara_api`, or `testara_plan`, ");
      sb.append("or call `testara_init` with `includeExamples=true` for demo files.\n");
    }
    return sb.toString();
  }

  // ── File content generators ───────────────────────────────────────────────

  private String generateFullPom(String type, String groupId, String artifactId, String basePkg, String engine, Path root) {
    String uiEngine = engine == null ? "selenium" : engine.toLowerCase(Locale.ROOT);
    String uiEngineDep = switch (uiEngine) {
      case "playwright" -> dep("testara-ui-playwright", null);
      case "appium"     -> dep("testara-ui-appium", null);
      default           -> dep("testara-ui-selenium", null);
    };
    String sliceDep = switch (type) {
      case "api"                          -> dep("testara-api", null) + dep("testara-api-cucumber", "test");
      case "ui"                           -> dep("testara-ui", null) + dep("testara-ui-cucumber", "test") + uiEngineDep;
      case "fullstack"                    -> dep("testara-api", null) + dep("testara-api-cucumber", "test")
                                           + dep("testara-ui", null) + dep("testara-ui-cucumber", "test") + uiEngineDep;
      case "database-sql", "sql"          -> dep("testara-database", null) + dep("testara-database-cucumber", "test");
      case "mongo", "database-mongo"      -> dep("testara-database", null) + dep("testara-database-cucumber", "test");
      case "kafka", "streaming"           -> dep("testara-streaming", null) + dep("testara-streaming-cucumber", "test");
      case "elastic", "elastic-search"    -> dep("testara-elastic", null) + dep("testara-elastic-cucumber", "test");
      default -> "";
    };
    String allDeps = dep("testara-command", null) + dep("testara-validation", null)
        + dep("testara-junit5", "test") + sliceDep
        + dep("org.projectlombok", "lombok", "${lombok.version}", "provided");
    String testaraVersion = versionResolver.resolve(root);
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
            <testara.version>%s</testara.version>
            <failsafe.version>3.5.1</failsafe.version>
            <lombok.version>1.18.42</lombok.version>
            <junit-platform.version>1.11.4</junit-platform.version>
            <jvm.options>
              --add-opens java.base/java.lang=ALL-UNNAMED
              --add-opens java.base/java.lang.reflect=ALL-UNNAMED
              --add-opens java.base/java.lang.invoke=ALL-UNNAMED
              --add-opens java.base/java.util=ALL-UNNAMED
              --add-opens java.base/java.net=ALL-UNNAMED
              --add-opens java.base/java.security=ALL-UNNAMED
              --add-opens java.base/java.util.concurrent=ALL-UNNAMED
              -Xms256m -Xmx512m
              -XX:+UseG1GC
              -Djava.awt.headless=true
              -Djava.net.preferIPv4Stack=true
            </jvm.options>
          </properties>

          <dependencies>
        %s      </dependencies>

          <profiles>
            <!-- Default: JUnit 4 runner via surefire-junit47 — mvn verify -->
            <profile>
              <id>junit4</id>
              <activation>
                <activeByDefault>true</activeByDefault>
              </activation>
              <properties>
                <it.test>%s.Junit4RunnerTests</it.test>
              </properties>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>${failsafe.version}</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.apache.maven.surefire</groupId>
                        <artifactId>surefire-junit47</artifactId>
                        <version>${failsafe.version}</version>
                      </dependency>
                    </dependencies>
                    <executions>
                      <execution>
                        <goals>
                          <goal>integration-test</goal>
                          <goal>verify</goal>
                        </goals>
                      </execution>
                    </executions>
                    <configuration>
                      <excludeJUnit5Engines>
                        <engine>testara-cucumber</engine>
                        <engine>junit-jupiter</engine>
                        <engine>junit-platform-suite</engine>
                        <engine>junit-vintage</engine>
                      </excludeJUnit5Engines>
                      <test>${it.test}</test>
                      <printSummary>false</printSummary>
                      <failIfNoTests>false</failIfNoTests>
                      <argLine>${jvm.options}</argLine>
                      <reuseForks>true</reuseForks>
                      <rerunFailingTestsCount>0</rerunFailingTestsCount>
                      <forkCount>1</forkCount>
                    </configuration>
                  </plugin>
                  <plugin>
                    <groupId>io.github.ygrip</groupId>
                    <artifactId>testara-reporter-plugin</artifactId>
                    <version>${testara.version}</version>
                    <executions>
                      <execution>
                        <phase>post-integration-test</phase>
                        <goals>
                          <goal>cucumber-summary</goal>
                        </goals>
                        <configuration>
                          <targetLocation>target/destination/</targetLocation>
                          <outputLocation>target/site/</outputLocation>
                          <reportTemplate>testara-style-report</reportTemplate>
                          <reportName>test-report</reportName>
                        </configuration>
                      </execution>
                    </executions>
                  </plugin>
                </plugins>
              </build>
            </profile>
            <!-- JUnit 5 runner — mvn verify -P junit5 -->
            <profile>
              <id>junit5</id>
              <properties>
                <it.test>%s.Junit5RunnerTests</it.test>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-console-standalone</artifactId>
                  <version>${junit-platform.version}</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>${failsafe.version}</version>
                    <executions>
                      <execution>
                        <goals>
                          <goal>integration-test</goal>
                          <goal>verify</goal>
                        </goals>
                      </execution>
                    </executions>
                    <configuration>
                      <includeJUnit5Engines>
                        <engine>testara-cucumber</engine>
                      </includeJUnit5Engines>
                      <excludeJUnit5Engines>
                        <engine>junit-jupiter</engine>
                        <engine>junit-platform-suite</engine>
                        <engine>junit-vintage</engine>
                      </excludeJUnit5Engines>
                      <test>${it.test}</test>
                      <printSummary>false</printSummary>
                      <failIfNoTests>false</failIfNoTests>
                      <argLine>${jvm.options}</argLine>
                      <reuseForks>true</reuseForks>
                      <rerunFailingTestsCount>0</rerunFailingTestsCount>
                      <forkCount>1</forkCount>
                    </configuration>
                  </plugin>
                </plugins>
              </build>
            </profile>
          </profiles>

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
                  <skip>true</skip>
                </configuration>
              </plugin>
            </plugins>
          </build>

          <repositories>
            <repository>
              <id>maven-central</id>
              <url>https://repo1.maven.org/maven2/</url>
            </repository>
          </repositories>
          <pluginRepositories>
            <pluginRepository>
              <id>maven-central</id>
              <url>https://repo1.maven.org/maven2/</url>
            </pluginRepository>
          </pluginRepositories>
        </project>
        """.formatted(groupId, artifactId, testaraVersion, allDeps,
        basePkg, basePkg);
  }

  private static String dep(String artifactId, String scope) {
    return dep("io.github.ygrip", artifactId, "${testara.version}", scope);
  }

  private static String dep(String groupId, String artifactId, String version, String scope) {
    String scopeTag = scope != null ? "\n              <scope>" + scope + "</scope>" : "";
    return """
              <dependency>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>%s
              </dependency>
          """.formatted(groupId, artifactId, version, scopeTag);
  }

  private String toKebab(String value) {
    return value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private String generateProperties(String type, String basePkg, String engine, boolean includeExamples) {
    String base = """
        # Scanner configuration
        class.loader.reject-packages=org.*,com.sun.*,java.*,javax.*,io.netty.*,org.springframework.*,net.bytebuddy.*,com.fasterxml.*,org.apache.*,org.junit.*,org.hamcrest.*,org.mockito.*,com.google.*,org.slf4j.*,ch.qos.logback.*,org.seleniumhq.*,net.serenitybdd.*,io.restassured.*,com.browserup.*,org.json.*,org.yaml.*,com.jayway.*,org.objenesis.*,net.sf.*,org.w3c.*,org.xml.*,com.squareup.*,okhttp3.*,retrofit2.*,com.github.*,io.github.classgraph.*,io.github.bonigarcia.*,org.jetbrains.*,kotlin.*,kotlinx.*
        class.loader.enable-parallel-scanning=false
        class.loader.buffer-size=33554432
        class.loader.default-scan-locations=io.github.ygrip.testara,%s
        class.loader.scan-locations.request-data=io.github.ygrip.testara,%s.data
        class.loader.scan-locations.response-data=io.github.ygrip.testara,%s.data

        # Command and validation configuration
        command.executor.scan-locations=io.github.ygrip.testara,%s.command
        validator.helper.scan-locations=io.github.ygrip.testara,%s.validation
        validator.helper.scan-timeout=5

        # Shared resources
        automation.config.template-folder=/src/test/resources/templates/
        automation.config.schema-folder=/src/test/resources/schemas/
        """.formatted(basePkg, basePkg, basePkg, basePkg, basePkg);

    String uiEngine = engine == null ? "selenium" : engine.toLowerCase(Locale.ROOT);
    String pageConfig = includeExamples ? """
          web.page.desktop.home.url=${APP_WEB_HOME_URL:http://localhost:3000}
          """ : "";
    String uiProperties = switch (uiEngine) {
      case "playwright" -> """
          # UI engine configuration
          automation.engine.default-engine=playwright
          automation.engine.active-engines=playwright
          automation.engine.screenshot-strategy=ON_EACH_STEP
          automation.engine.screenshot-output-type=IMAGE
          automation.engine.screenshot-fps=30
          automation.engine.force-resolution=true
          playwright.browser.owner=testara
          playwright.browser.headless=false
          playwright.browser.scan-locations=io.github.ygrip.testara,%s
          playwright.browser.page-scan-locations=io.github.ygrip.testara,%s
          playwright.browser.action-scan-locations=io.github.ygrip.testara,%s
          playwright.browser.remote-driver.default.enabled=false
          playwright.browser.remote-driver.default.uri=${UI_REMOTE_URL:http://localhost:4444/}
          %s""".formatted(basePkg, basePkg, basePkg, pageConfig);
      default -> """
          # UI engine configuration
          automation.engine.default-engine=selenium
          automation.engine.active-engines=selenium
          automation.engine.screenshot-strategy=ON_EACH_STEP
          automation.engine.screenshot-output-type=IMAGE
          automation.engine.screenshot-fps=30
          automation.engine.force-resolution=true
          selenium.driver.owner=testara
          selenium.driver.headless=false
          selenium.driver.scan-locations=io.github.ygrip.testara,%s
          selenium.driver.page-scan-locations=io.github.ygrip.testara,%s
          selenium.driver.action-scan-locations=io.github.ygrip.testara,%s
          selenium.driver.remote-driver.default.enabled=false
          selenium.driver.remote-driver.default.uri=${UI_REMOTE_URL:http://localhost:4444/}
          %s""".formatted(basePkg, basePkg, basePkg, pageConfig);
    };

    return base + switch (type) {
      case "api" -> apiProperties(includeExamples);
      case "ui" -> uiProperties;
      case "fullstack" -> apiProperties(includeExamples) + "\n" + uiProperties;
      case "sql", "database-sql" -> includeExamples ? """
          # Database configuration
          sql.service.settlementDb.uri=${DB_SETTLEMENT_URI:jdbc:postgresql://localhost:5432/testdb}
          sql.service.settlementDb.username=${DB_SETTLEMENT_USERNAME:testuser}
          sql.service.settlementDb.password=${DB_SETTLEMENT_PASSWORD:testpass}
          sql.service.settlementDb.dbType=POSTGRESQL
          """ : deferredConfig("SQL", "sql.service.{alias}.uri|username|password|dbType");
      case "mongo", "database-mongo" -> includeExamples ? """
          # MongoDB configuration
          mongo.service.productDb.connectionString=${MONGO_PRODUCT_CONNECTION_STRING:mongodb://localhost:27017}
          mongo.service.productDb.dbName=${MONGO_PRODUCT_DB_NAME:testdb}
          """ : deferredConfig("MongoDB", "mongo.service.{alias}.connectionString|dbName");
      case "kafka", "streaming" -> includeExamples ? """
          # Kafka configuration
          kafka.service.orderStream.servers=${KAFKA_ORDER_SERVERS:localhost:9092}
          kafka.service.orderStream.groupId=${KAFKA_ORDER_GROUP_ID:testara-order-tests}
          kafka.service.orderStream.topics.orders=${KAFKA_TOPIC_ORDERS:orders}
          """ : deferredConfig("Kafka", "kafka.service.{alias}.servers|groupId|topics.{topic}");
      case "elastic", "elastic-search" -> includeExamples ? """
          # ElasticSearch configuration
          elasticsearch.service.catalog.hosts[0]=${ELASTICSEARCH_CATALOG_HOST:http://localhost:9200}
          elasticsearch.service.catalog.username=${ELASTICSEARCH_CATALOG_USERNAME:}
          elasticsearch.service.catalog.password=${ELASTICSEARCH_CATALOG_PASSWORD:}
          elasticsearch.service.catalog.secured=false
          elasticsearch.service.catalog.requireAuthentication=false
          """ : deferredConfig("ElasticSearch", "elasticsearch.service.{alias}.hosts[0]|username|password|secured|requireAuthentication");
      default -> "";
    };
  }

  private String apiProperties(boolean includeExamples) {
    if (!includeExamples) {
      return deferredConfig("API", "api.service.{alias}.host|basePath|default_specification and spec.api.{alias}.*");
    }
    return """
        # API service configuration
        api.service.sample-api.host=${API_SAMPLE_API_HOST:http://localhost:8080}
        api.service.sample-api.basePath=${API_SAMPLE_API_BASE_PATH:/api/v1}
        api.service.sample-api.default_specification=sample-api
        spec.api.sample-api.header.Content-Type=application/json
        spec.api.sample-api.header.Accept=application/json
        """;
  }

  private String deferredConfig(String slice, String shape) {
    return """
        # %s configuration
        # Add contextual %s keys before generating or running features.
        # Expected shape: %s
        """.formatted(slice, slice, shape);
  }

  private String generateCucumberProperties(String type, String basePkg, boolean includeExamples) {
    String tagFilter = tagFilter(type, includeExamples);
    boolean isUi = type.equals("ui") || type.equals("fullstack");
    String stepListener = isUi ? ",io.github.ygrip.testara.ui.listeners.StepListener" : "";
    return """
        cucumber.publish.enabled=false
        cucumber.publish.quiet=true
        cucumber.object-factory=io.github.ygrip.testara.cucumber.factory.TestaraObjectFactory
        cucumber.glue=io.github.ygrip.testara,%s
        cucumber.plugin=html:target/destination/cucumber.html,json:target/destination/cucumber.json,rerun:target/rerun/rerun.txt%s
        cucumber.snippet-type=camelcase
        cucumber.execution.dry-run=false
        cucumber.step.notifications.enabled=false
        cucumber.filter.skipped.scenarios=true
        cucumber.features=classpath:features
        cucumber.filter.tags=%s
        cucumber.rerun.strategy=NONE
        cucumber.max.retry.failed.scenarios=0
        cucumber.execution.parallel.enabled=false
        cucumber.execution.parallel.virtual-thread.enabled=false
        cucumber.execution.parallel.virtual-thread.max-threads=32
        cucumber.execution.parallel.config.strategy=dynamic
        cucumber.execution.parallel.config.fixed.parallelism=4
        """.formatted(basePkg, stepListener, tagFilter);
  }

  private String generateJunitPlatformProperties() {
    return """
        # JUnit 5 Platform specific settings (cucumber.properties provides the rest)
        cucumber.junit-platform.naming-strategy=long
        junit.jupiter.execution.parallel.enabled=false
        """;
  }

  private String generateApplicationProperties(String type, boolean includeExamples) {
    String common = """
        # Consul properties
        config.consul.enabled=${CONSUL_ENABLED:false}
        config.consul.host=${CONSUL_HOST:localhost}
        config.consul.port=${CONSUL_PORT:8500}
        config.consul.acl-token=${CONSUL_TOKEN:local-root-token}
        config.consul.prefix=${CONFIG_PATH:config/testara-automation}/${TEST_ENV:qa}/

        # Vault properties
        config.vault.enabled=${VAULT_ENABLED:false}
        config.vault.address=${VAULT_HOST:http://127.0.0.1:8200}
        config.vault.token=${VAULT_TOKEN:myroot}
        config.vault.engine-version=${VAULT_ENGINE_VERSION:2}
        config.vault.path=${VAULT_PATH:config/testara-automation}/${TEST_ENV:qa}

        # User-defined and environment values referenced from Testara config/features via properties(key).
        """;
    String uiCommon = type.equals("ui") || type.equals("fullstack") ? """
        ui.remote.url=http://localhost:4444/
        """ : "";
    if (!includeExamples) return common + uiCommon;
    return common + uiCommon + switch (type) {
      case "ui" -> """
          app.web.home-url=http://localhost:3000
          """;
      case "api", "fullstack" -> """
          app.web.home-url=http://localhost:3000
          api.sample-api.host=http://localhost:8080
          test.sample.id=00000000-0000-0000-0000-000000000001
          """;
      case "sql", "database-sql" -> """
          db.settlement.uri=jdbc:postgresql://localhost:5432/testdb
          db.settlement.username=testuser
          db.settlement.password=testpass
          """;
      case "mongo", "database-mongo" -> """
          mongo.product.connection-string=mongodb://localhost:27017
          mongo.product.db-name=testdb
          """;
      case "kafka", "streaming" -> """
          kafka.order.servers=localhost:9092
          kafka.order.group-id=testara-order-tests
          kafka.topic.orders=orders
          """;
      case "elastic", "elastic-search" -> """
          elasticsearch.catalog.host=http://localhost:9200
          elasticsearch.catalog.username=
          elasticsearch.catalog.password=
          elastic.index.products=products
          test.product.id=00000000-0000-0000-0000-000000000001
          """;
      default -> "";
    };
  }

  private String generateLog4j2Config(String basePkg) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Configuration>
          <Appenders>
            <Console name="Console" target="SYSTEM_OUT">
              <PatternLayout pattern="%%d{yyyy-MM-dd HH:mm:ss.SSS} %%-5level [%%t] (%%F:%%L).%%M - %%m%%n"/>
            </Console>
          </Appenders>

          <Loggers>
            <Root level="warn">
              <AppenderRef ref="Console"/>
            </Root>
            <Logger name="io.github.ygrip.testara" level="info" additivity="false">
              <AppenderRef ref="Console"/>
            </Logger>
            <Logger name="%s" level="info" additivity="false">
              <AppenderRef ref="Console"/>
            </Logger>
          </Loggers>
        </Configuration>
        """.formatted(basePkg);
  }

  private String generateJunit5Runner(String basePkg) {
    return """
        package %s;

        import io.github.ygrip.testara.engine.suites.TestSuite;
        import lombok.extern.log4j.Log4j2;

        //@formatter:off
        @Log4j2
        @TestSuite
        public class Junit5RunnerTests {

        }
        //@formatter:on
        """.formatted(basePkg);
  }

  private String generateJunit4Runner(String basePkg) {
    return """
        package %s;

        import org.junit.runner.RunWith;

        import io.cucumber.junit.Cucumber;
        import io.cucumber.junit.CucumberOptions;

        //@formatter:off
        @RunWith(Cucumber.class)
        @CucumberOptions(
            features = "classpath:features",
            glue = {"io.github.ygrip.testara", "%s"}
        )
        public class Junit4RunnerTests {
        }
        //@formatter:on
        """.formatted(basePkg, basePkg);
  }

  private String tagFilter(String type, boolean includeExamples) {
    String sample = includeExamples ? " or @sample" : "";
    return switch (type) {
      case "api" -> "(@api%s) and not (@manual or @deprecated or @ignored)".formatted(sample);
      case "fullstack" -> "(@api or @ui or @fullstack%s) and not (@manual or @deprecated or @ignored)".formatted(sample);
      case "ui" -> "(@ui%s) and not (@manual or @deprecated or @ignored)".formatted(sample);
      case "sql", "database-sql", "mongo", "database-mongo" ->
          "(@database%s) and not (@manual or @deprecated or @ignored)".formatted(sample);
      case "kafka", "streaming" -> "(@streaming%s) and not (@manual or @deprecated or @ignored)".formatted(sample);
      case "elastic", "elastic-search" -> "(@elastic%s) and not (@manual or @deprecated or @ignored)".formatted(sample);
      default -> "not (@manual or @deprecated or @ignored)";
    };
  }

  private String generateStepDefinitions(String basePkg, String type) {
    return """
        package %s.steps;

        import io.cucumber.java.en.*;

        /**
         * Project-specific step definitions.
         * Testara provides built-in steps via io.github.ygrip.testara glue:
         *   - API: [api] using service, [api] process request, [api] response statusCode...
         *   - UI:  user using chrome in desktop, user click the "element"...
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

  private String generatePageObject(String basePkg, String engine) {
    boolean playwright = "playwright".equalsIgnoreCase(engine);
    String pageBaseImport = playwright
        ? "io.github.ygrip.testara.ui.playwright.page.PlaywrightPage"
        : "io.github.ygrip.testara.ui.selenium.page.SeleniumPage";
    String pageBaseClass = playwright ? "PlaywrightPage" : "SeleniumPage";
    return """
        package %s.page;

        import io.github.ygrip.testara.ui.model.DeviceType;
        import io.github.ygrip.testara.ui.model.Locator;
        import io.github.ygrip.testara.ui.model.Page;
        import %s;

        @Page(name = "home", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
        public class HomePage extends %s {
          private static final Locator SEARCH_INPUT = Locator.css("[name='q']");
          private static final Locator SEARCH_BUTTON = Locator.css("button[type='submit']");
          private static final Locator ERROR_MESSAGE = Locator.css("[data-testid='error-message']");
        }
        """.formatted(basePkg, pageBaseImport, pageBaseClass);
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
              When [api] process request to "files/sample/request/sample-get"
              Then [api] response statusCode should be 200
              Then [api] assign previous response data to sampleResponse

            @P2 @negative
            Scenario: Get resource with invalid id returns 404
              Given [api] prepare pathParam for id with value "invalid-id"
              When [api] process request to "files/sample/request/sample-get"
              Then [api] response statusCode should be 404
              Then [api] response success should be false
          """;
      case "ui", "fullstack" -> """
          # Sample UI feature — uses Testara built-in UIBaseSteps.
          # Replace with your actual page flow.

          @ui @sample @regression
          Feature: Sample UI test

            Background:
              Given user using chrome in desktop

            @P1 @positive
            Scenario: User navigates to home page
              When user open "home" page
              Then user is in "home" page

            @P2 @negative
            Scenario: User sees error on invalid input
              When user open "home" page
              Then user is in "home" page
              When user type value "invalid" to "search input"
              And user click the "search button"
              Then user see that
                | actual        | validation | expectation |
                | error message | DISPLAYED   | true        |
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
      case "kafka", "streaming" -> """
          # Sample Kafka feature — uses Testara built-in Kafka steps.

          @kafka @streaming @sample @regression
          Feature: Sample Kafka validation

            @P1
            Scenario: Publish order event
              Given user start kafka producer for orderStream
              When user send kafka message to topic "properties(kafka.topic.orders)" with data "{\"id\":\"uuid()\"}"
              Then user stop kafka producer
          """;
      case "elastic", "elastic-search" -> """
          # Sample ElasticSearch feature — uses Testara built-in ElasticSearch steps.

          @elastic @sample @regression
          Feature: Sample ElasticSearch validation

            @P1
            Scenario: Product document exists
              Given [elastic-search] connect to elastic search with name catalog
              When [elastic-search] assign data productResults from index products with query :
                | query | {"query":{"term":{"id":"properties(test.product.id)"}}} |
              Then [elastic-search] assign previous elastic search response to productResults
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
