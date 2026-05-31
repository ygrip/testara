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
        ? applyFiles(type, groupId, artifactId, basePkg, pkgPath, input.engine(), integrate, context.projectRoot(), compile)
        : renderPreview(type, groupId, artifactId, basePkg, pkgPath, input.engine(), integrate, context.projectRoot());
  }

  // ── Write mode ────────────────────────────────────────────────────────────

  private String applyFiles(String type, String groupId, String artifactId, String basePkg, String pkgPath, String engine,
      boolean integrate, Path root, boolean compile) {
    List<String> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    try {
      Path pom = root.resolve("pom.xml");
      if (integrate && Files.exists(pom)) {
        skipped.add("pom.xml (existing project — add dependencies manually, see preview)");
      } else if (!Files.exists(pom)) {
        writeFile(pom, generateFullPom(type, groupId, artifactId, engine, root));
        created.add("pom.xml");
      } else {
        skipped.add("pom.xml (already exists)");
      }

      mkdirs(root, "src/test/resources/features/" + type);
      mkdirs(root, "src/test/resources/files");
      mkdirs(root, "src/test/resources/validations");
      mkdirs(root, "src/test/java/" + pkgPath + "/runner");
      mkdirs(root, "src/test/java/" + pkgPath + "/steps");
      mkdirs(root, "src/main/java/" + pkgPath + "/command");
      mkdirs(root, "src/main/java/" + pkgPath + "/validation");

      writeIfAbsent(root, "src/test/resources/configuration.properties",
          generateProperties(type, basePkg, engine), created, skipped);
      writeIfAbsent(root, "src/test/resources/cucumber.properties",
          generateCucumberProperties(), created, skipped);
      writeIfAbsent(root, "src/test/resources/junit-platform.properties",
          generateJunitPlatformProperties(type, basePkg), created, skipped);
      writeIfAbsent(root, "src/test/resources/application.properties",
          generateApplicationProperties(type), created, skipped);
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
        mkdirs(root, "src/main/java/" + pkgPath + "/page");
        mkdirs(root, "src/main/java/" + pkgPath + "/action");
        writeIfAbsent(root, "src/main/java/" + pkgPath + "/page/HomePage.java",
            generatePageObject(basePkg, engine), created, skipped);
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

  private String renderPreview(String type, String groupId, String artifactId, String basePkg, String pkgPath,
      String engine, boolean integrate, Path root) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Init: ").append(type.toUpperCase(Locale.ROOT)).append(" Project\n\n");
    sb.append(integrate ? "> Integration mode\n\n" : "> Bootstrap mode\n\n");

    sb.append("## pom.xml\n\n```xml\n").append(generateFullPom(type, groupId, artifactId, engine, root)).append("```\n\n");
    sb.append("## configuration.properties\n\n```properties\n").append(generateProperties(type, basePkg, engine)).append("```\n\n");
    sb.append("## cucumber.properties\n\n```properties\n").append(generateCucumberProperties()).append("```\n\n");
    sb.append("## junit-platform.properties\n\n```properties\n").append(generateJunitPlatformProperties(type, basePkg)).append("```\n\n");
    sb.append("## application.properties\n\n```properties\n").append(generateApplicationProperties(type)).append("```\n\n");
    sb.append("## TestRunner.java\n\n```java\n").append(generateRunner(basePkg)).append("```\n\n");
    sb.append("## sample.feature\n\n```gherkin\n").append(generateSampleFeature(type, basePkg)).append("```\n");
    if (type.equals("ui") || type.equals("fullstack")) {
      sb.append("\n## HomePage.java\n\n```java\n").append(generatePageObject(basePkg, engine)).append("```\n");
    }
    if (type.equals("api") || type.equals("fullstack")) {
      sb.append("\n## sample-get.json\n\n```json\n").append(generateRequestSpec(basePkg, type)).append("```\n");
    }
    return sb.toString();
  }

  // ── File content generators ───────────────────────────────────────────────

  private String generateFullPom(String type, String groupId, String artifactId, String engine, Path root) {
    String uiEngine = engine == null ? "selenium" : engine.toLowerCase(Locale.ROOT);
    String uiEngineDependency = switch (uiEngine) {
      case "playwright" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui-playwright</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      case "appium" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui-appium</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      default -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui-selenium</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
    };
    String sliceDep = switch (type) {
      case "ui", "fullstack" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-ui-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """ + uiEngineDependency;
      case "database-sql", "sql" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-database-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      case "mongo", "database-mongo" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-database-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      case "kafka", "streaming" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-streaming-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      case "elastic", "elastic-search" -> """
              <dependency>
                <groupId>io.github.ygrip</groupId>
                <artifactId>testara-elastic-cucumber</artifactId>
                <version>${testara.version}</version>
                <scope>test</scope>
              </dependency>
          """;
      default -> "";
    };
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
          </properties>

          <dependencies>
            <dependency>
              <groupId>io.github.ygrip</groupId>
              <artifactId>testara-api-cucumber</artifactId>
              <version>${testara.version}</version>
              <scope>test</scope>
            </dependency>
            <dependency>
              <groupId>io.github.ygrip</groupId>
              <artifactId>testara-junit5</artifactId>
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
        """.formatted(groupId, artifactId, testaraVersion, sliceDep);
  }

  private String generateProperties(String type, String basePkg, String engine) {
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
    String uiProperties = switch (uiEngine) {
      case "playwright" -> """
          # UI engine configuration
          automation.engine.default-engine=playwright
          automation.engine.active-engines=playwright
          automation.engine.screenshot-strategy=ON_EACH_STEP
          automation.engine.screenshot-output-type=VIDEO
          playwright.browser.owner=testara
          playwright.browser.headless=true
          playwright.browser.scan-locations=io.github.ygrip.testara,%s
          playwright.browser.page-scan-locations=io.github.ygrip.testara,%s
          playwright.browser.action-scan-locations=io.github.ygrip.testara,%s
          playwright.browser.remote-driver.default.enabled=false
          playwright.browser.remote-driver.default.uri=properties(ui.remote.url)
          web.page.desktop.home.url=properties(app.web.home-url)
          """.formatted(basePkg, basePkg, basePkg);
      default -> """
          # UI engine configuration
          automation.engine.default-engine=selenium
          automation.engine.active-engines=selenium
          automation.engine.screenshot-strategy=ON_EACH_STEP
          automation.engine.screenshot-output-type=VIDEO
          selenium.driver.owner=testara
          selenium.driver.headless=true
          selenium.driver.scan-locations=io.github.ygrip.testara,%s
          selenium.driver.page-scan-locations=io.github.ygrip.testara,%s
          selenium.driver.action-scan-locations=io.github.ygrip.testara,%s
          selenium.driver.remote-driver.default.enabled=false
          selenium.driver.remote-driver.default.uri=properties(ui.remote.url)
          web.page.desktop.home.url=properties(app.web.home-url)
          """.formatted(basePkg, basePkg, basePkg);
    };

    return base + switch (type) {
      case "api" -> apiProperties();
      case "ui" -> uiProperties;
      case "fullstack" -> apiProperties() + "\n" + uiProperties;
      case "sql", "database-sql" -> """
          # Database configuration
          sql.service.settlementDb.uri=properties(db.settlement.uri)
          sql.service.settlementDb.username=properties(db.settlement.username)
          sql.service.settlementDb.password=properties(db.settlement.password)
          sql.service.settlementDb.dbType=POSTGRESQL
          """;
      case "mongo", "database-mongo" -> """
          # MongoDB configuration
          mongo.service.productDb.connectionString=properties(mongo.product.connection-string)
          mongo.service.productDb.dbName=properties(mongo.product.db-name)
          """;
      case "kafka", "streaming" -> """
          # Kafka configuration
          kafka.service.orderStream.servers=properties(kafka.order.servers)
          kafka.service.orderStream.groupId=properties(kafka.order.group-id)
          kafka.service.orderStream.topics.orders=properties(kafka.topic.orders)
          """;
      case "elastic", "elastic-search" -> """
          # ElasticSearch configuration
          elasticsearch.service.catalog.hosts[0]=properties(elasticsearch.catalog.host)
          elasticsearch.service.catalog.username=properties(elasticsearch.catalog.username)
          elasticsearch.service.catalog.password=properties(elasticsearch.catalog.password)
          elasticsearch.service.catalog.secured=false
          elasticsearch.service.catalog.requireAuthentication=false
          """;
      default -> "";
    };
  }

  private String apiProperties() {
    return """
        # API service configuration
        api.service.sample-api.host=properties(api.sample-api.host)
        api.service.sample-api.basePath=/api/v1
        api.service.sample-api.default_specification=sample-api
        spec.api.sample-api.header.Content-Type=application/json
        spec.api.sample-api.header.Accept=application/json
        """;
  }

  private String generateCucumberProperties() {
    return """
        cucumber.publish.quiet=true
        cucumber.publish.enabled=false
        cucumber.object-factory=io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory
        cucumber.plugin=html:target/destination/cucumber.html,\\
          json:target/destination/cucumber.json,rerun:target/rerun/rerun.txt
        """;
  }

  private String generateJunitPlatformProperties(String type, String basePkg) {
    String tagFilter = switch (type) {
      case "ui" -> "(@ui or @sample) and not (@manual or @deprecated or @ignored)";
      case "fullstack" -> "(@api or @ui or @fullstack or @sample) and not (@manual or @deprecated or @ignored)";
      case "sql", "database-sql", "mongo", "database-mongo" ->
          "(@database or @sample) and not (@manual or @deprecated or @ignored)";
      default -> "(@api or @sample) and not (@manual or @deprecated or @ignored)";
    };
    return """
        cucumber.publish.quiet=true
        cucumber.publish.enabled=false
        cucumber.snippet-type=camelcase
        cucumber.execution.dry-run=false
        cucumber.junit-platform.naming-strategy=CUSTOM
        cucumber.step.notifications.enabled=false
        cucumber.filter.skipped.scenarios=true
        cucumber.rerun.strategy=DEFERRED
        cucumber.max.retry.failed.scenarios=2
        cucumber.execution.parallel.enabled=true
        cucumber.execution.parallel.virtual-thread.enabled=true
        cucumber.execution.parallel.virtual-thread.max-threads=32
        cucumber.execution.parallel.config.strategy=dynamic
        cucumber.execution.parallel.config.fixed.parallelism=4
        junit.jupiter.execution.parallel.enabled=true
        cucumber.object-factory=io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory
        cucumber.glue=io.github.ygrip.testara,%s
        cucumber.filter.tags=%s
        cucumber.features=src/test/resources/features/
        # cucumber.features=@target/rerun/rerun.txt
        """.formatted(basePkg, tagFilter);
  }

  private String generateApplicationProperties(String type) {
    String common = """
        # Environment values referenced from Testara config/features via properties(key).
        app.web.home-url=http://localhost:3000
        ui.remote.url=http://localhost:4444/
        """;
    return common + switch (type) {
      case "api", "fullstack" -> """
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

  private String generateRunner(String basePkg) {
    return """
        package %s.runner;

        import io.cucumber.junit.platform.engine.Constants;
        import io.github.ygrip.testara.engine.suites.TestSuite;
        import org.junit.platform.suite.api.ConfigurationParameter;

        @TestSuite
        @ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME,
            value = "io.github.ygrip.testara,%s")
        @ConfigurationParameter(key = Constants.FEATURES_PROPERTY_NAME,
            value = "src/test/resources/features")
        @ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
            value = "html:target/destination/cucumber.html,json:target/destination/cucumber.json,rerun:target/rerun/rerun.txt")
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
              Then user should see "error message" is displayed
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
