package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInitSkillTest {

  @TempDir
  Path projectRoot;

  @AfterEach
  void clearVersionOverride() {
    System.clearProperty("testara.agent.version");
  }

  @Test
  void previewUsesCurrentTestaraVersionInsteadOfStaleLiteral() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", "io.github.ygrip.sample", "selenium", false),
        autoContext());

    assertTrue(output.contains("<testara.version>2.0.5</testara.version>"));
    assertFalse(output.contains("<testara.version>2.0.1</testara.version>"));
  }

  @Test
  void explicitAgentVersionTakesPrecedence() {
    System.setProperty("testara.agent.version", "9.8.7");

    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("ui", "io.github.ygrip.sample", "selenium", false),
        autoContext());

    assertTrue(output.contains("<testara.version>9.8.7</testara.version>"));
  }

  @Test
  void generatedPomUsesRequestedMavenCoordinates() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", null, "selenium", false, "com.acme.qa", "checkout-tests"),
        context());

    assertTrue(output.contains("<groupId>com.acme.qa</groupId>"));
    assertTrue(output.contains("<artifactId>checkout-tests</artifactId>"));
    assertTrue(output.contains("package com.acme.qa.checkouttests;"));
    assertTrue(output.contains("public class Junit5RunnerTests"));
    assertTrue(output.contains("public class Junit4RunnerTests"));
    assertTrue(output.contains("@TestSuite"));
    assertFalse(output.contains("Constants.GLUE_PROPERTY_NAME"));
    assertFalse(output.contains("public class TestRunner"));
  }

  @Test
  void apiPreviewUsesTestaraRequestSpecStructure() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", "io.github.ygrip.example", "selenium", false,
            "io.github.ygrip", "api-automation"),
        context());

    assertTrue(output.contains("## log4j2.xml"));
    assertTrue(output.contains("<PatternLayout"));
    assertTrue(output.contains("io.github.ygrip.testara"));
    assertTrue(output.contains("<artifactId>testara-api-cucumber</artifactId>"));
    assertDependencyScope(output, "testara-api", null);
    assertDependencyScope(output, "testara-api-cucumber", "test");
    assertDependencyScope(output, "testara-command", null);
    assertDependencyScope(output, "testara-validation", null);
    assertTrue(output.contains("<artifactId>testara-junit5</artifactId>"));
    assertDependencyScope(output, "testara-junit5", "test");
    assertTrue(output.contains("<artifactId>lombok</artifactId>"));
    assertFalse(output.contains("<artifactId>junit-platform-suite</artifactId>"));
    assertTrue(output.contains("<artifactId>maven-failsafe-plugin</artifactId>"));
    assertTrue(output.contains("<goal>integration-test</goal>"));
    assertTrue(output.contains("<goal>verify</goal>"));
    assertTrue(output.contains("<artifactId>maven-enforcer-plugin</artifactId>"));
    assertTrue(output.contains("<id>require-java-21-for-testara</id>"));
    assertTrue(output.contains("<version>[21,)</version>"));
    assertTrue(output.contains("Testara ${testara.version} and testara-reporter-plugin require Maven to run with Java 21+"));
    assertTrue(output.contains("Maven must run with Java 21+"));
    assertTrue(output.contains("<artifactId>maven-surefire-plugin</artifactId>"));
    assertTrue(output.contains("<skip>true</skip>"));
    assertFalse(output.contains("<artifactId>maven-dependency-plugin</artifactId>"));
    assertFalse(output.contains("unpack-step-definitions"));
    assertFalse(output.contains("target/step_definitions/src"));
    // it.test is now inside profiles, not top-level properties
    assertTrue(output.contains("<it.test>io.github.ygrip.example.Junit5RunnerTests</it.test>"));
    assertTrue(output.contains("<it.test>io.github.ygrip.example.Junit4RunnerTests</it.test>"));
    assertTrue(output.contains("<id>junit4</id>"));
    assertTrue(output.contains("<id>junit5</id>"));
    assertTrue(output.contains("<activeByDefault>true</activeByDefault>"));
    assertTrue(output.contains("surefire-junit47"));
    assertTrue(output.contains("testara-cucumber</engine>"));
    assertTrue(output.contains("testara-reporter-plugin"));
    assertTrue(output.contains("<engine>testara-cucumber</engine>"));
    // reporter plugin uses its own default paths — no override in generated POM
    assertFalse(output.contains("<targetLocation>"));
    assertFalse(output.contains("<outputLocation>"));
    assertTrue(output.contains("import io.github.ygrip.testara.engine.suites.TestSuite;"));
    assertFalse(output.contains("@IncludeEngines(\"cucumber\")"));
    assertTrue(output.contains("class.loader.default-scan-locations=io.github.ygrip.testara,io.github.ygrip.example"));
    assertTrue(output.contains("# API configuration"));
    assertTrue(output.contains("api.service.{alias}.host|basePath|default_specification"));
    assertFalse(output.contains("api.service.sample-api.host=properties(api.sample-api.host)"));
    assertFalse(output.contains("api.sample-api.host=http://localhost:8080"));
    assertTrue(output.contains("config.consul.enabled=${CONSUL_ENABLED:false}"));
    assertTrue(output.contains("config.vault.enabled=${VAULT_ENABLED:false}"));
    assertConfigurationPropertiesDoNotContainUserValues(output);
    assertTrue(output.contains("## cucumber.properties"));
    assertTrue(output.contains("## junit-platform.properties"));
    // cucumber.properties uses TestaraObjectFactory (JUnit4 + JUnit5 compatible)
    assertTrue(output.contains("cucumber.object-factory=io.github.ygrip.testara.cucumber.factory.TestaraObjectFactory"));
    assertFalse(output.contains("cucumber.object-factory=io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory"));
    // junit-platform.properties is now JUnit5-specific only; rest is in cucumber.properties
    assertTrue(output.contains("cucumber.junit-platform.naming-strategy=long"));
    assertFalse(output.contains("cucumber.junit-platform.naming-strategy=CUSTOM"));
    // parallel/retry/tags are now in cucumber.properties (apply to both runners)
    assertTrue(output.contains("cucumber.max.retry.failed.scenarios=0"));
    assertTrue(output.contains("cucumber.execution.parallel.enabled=false"));
    assertTrue(output.contains("cucumber.filter.tags=(@api) and not (@manual or @deprecated or @ignored)"));
    assertTrue(output.contains("cucumber.features=classpath:features"));
    assertTrue(output.contains("cucumber.glue=io.github.ygrip.testara,io.github.ygrip.example"));
    assertFalse(output.contains("Given [api] using service with alias sample-api"));
    assertFalse(output.contains("When [api] process request to \"files/sample/request/sample-get\""));
    assertFalse(output.contains("Then [api] response statusCode should be 200"));
    assertTrue(output.contains("@RunWith(Cucumber.class)"));
    // Junit4RunnerTests now matches sample project: no objectFactory, uses classpath:features
    assertFalse(output.contains("objectFactory = TestaraObjectFactory.class"));
    assertTrue(output.contains("features = \"classpath:features\""));
    assertTrue(output.contains("glue = {\"io.github.ygrip.testara\", \"io.github.ygrip.example\"}"));
  }

  @Test
  void seleniumUiPreviewUsesExplicitUiDependenciesAndScanLocations() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("ui", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "ui-automation"),
        confirmedEngineContext());

    assertTrue(output.contains("<artifactId>testara-ui</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-cucumber</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-selenium</artifactId>"));
    assertDependencyScope(output, "testara-ui", null);
    assertDependencyScope(output, "testara-ui-selenium", null);
    assertDependencyScope(output, "testara-ui-cucumber", "test");
    assertTrue(output.contains("selenium.driver.page-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("selenium.driver.headless=false"));
    assertFalse(output.contains("selenium.driver.headless=true"));
    assertTrue(output.contains("automation.engine.screenshot-output-type=IMAGE"));
    assertFalse(output.contains("automation.engine.screenshot-output-type=VIDEO"));
    assertFalse(output.contains("public class HomePage extends SeleniumPage"));
    assertFalse(output.contains("private static final Locator SEARCH_INPUT"));
    assertFalse(output.contains("StepDefinitions"));
    assertFalse(output.contains("sample.feature"));
  }

  @Test
  void uiPreviewUsesExampleProjectConventions() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("ui", "io.github.ygrip.automation", "playwright", false,
            "io.github.ygrip", "ui-automation"),
        confirmedEngineContext());

    assertTrue(output.contains("<artifactId>testara-ui</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-cucumber</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-playwright</artifactId>"));
    assertDependencyScope(output, "testara-ui-playwright", null);
    assertTrue(output.contains("class.loader.default-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("playwright.browser.page-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertFalse(output.contains("web.page.desktop.home.url=properties(app.web.home-url)"));
    assertFalse(output.contains("app.web.home-url=http://localhost:3000"));
    assertEquals(1, countOccurrences(output, "automation.engine.default-engine=playwright"));
    assertEquals(1, countOccurrences(output, "automation.engine.active-engines=playwright"));
    assertTrue(output.contains("automation.engine.screenshot-output-type=IMAGE"));
    assertFalse(output.contains("automation.engine.screenshot-output-type=VIDEO"));
    assertFalse(output.contains("automation.engine.default-engine=selenium"));
    assertFalse(output.contains("Given user using chrome in desktop"));
    assertFalse(output.contains("Then user is in \"home\" page"));
    assertFalse(output.contains("user using web in desktop"));
    assertFalse(output.contains("io.github.ygrip.testara.ui.page.PageContext"));
  }

  @Test
  void defaultInitPreviewOmitsDemoArtifactsAndPlaceholders() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("fullstack", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "automation"),
        confirmedEngineContext());

    assertFalse(output.contains("StepDefinitions.java"));
    assertFalse(output.contains("public class StepDefinitions"));
    assertFalse(output.contains("HomePage.java"));
    assertFalse(output.contains("public class HomePage"));
    assertFalse(output.contains("sample.feature"));
    assertFalse(output.contains("sample-get.json"));
    assertFalse(output.contains("@sample"));
    assertTrue(output.contains("Generate contextual artifacts with `testara_ui`, `testara_api`, or `testara_plan`"));
  }

  @Test
  void examplesOptionKeepsDemoArtifactsExplicit() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("fullstack", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "automation"),
        examplesContext());

    assertFalse(output.contains("StepDefinitions.java"));
    assertFalse(output.contains("public class StepDefinitions"));
    assertTrue(output.contains("HomePage.java"));
    assertTrue(output.contains("public class HomePage extends SeleniumPage"));
    assertTrue(output.contains("sample.feature"));
    assertTrue(output.contains("sample-get.json"));
    assertTrue(output.contains("api.service.sample-api.host=${API_SAMPLE_API_HOST:http://localhost:8080}"));
    assertFalse(output.contains("api.service.sample-api.host=properties(api.sample-api.host)"));
    assertTrue(output.contains("cucumber.filter.tags=(@api or @ui or @fullstack or @sample) and not (@manual or @deprecated or @ignored)"));
  }

  @Test
  void dbKafkaElasticPreviewUsesFrameworkPropertyPrefixes() {
    TestInitSkill skill = new TestInitSkill();

    String sql = skill.execute(new TestInitSkill.Input("sql", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "sql-automation"),
        context());
    assertTrue(sql.contains("<artifactId>testara-database</artifactId>"));
    assertTrue(sql.contains("<artifactId>testara-database-cucumber</artifactId>"));
    assertDependencyScope(sql, "testara-database", null);
    assertDependencyScope(sql, "testara-database-cucumber", "test");
    assertTrue(sql.contains("Expected shape: sql.service.{alias}.uri|username|password|dbType"));

    String kafka = skill.execute(new TestInitSkill.Input("kafka", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "kafka-automation"),
        context());
    assertTrue(kafka.contains("<artifactId>testara-streaming</artifactId>"));
    assertTrue(kafka.contains("<artifactId>testara-streaming-cucumber</artifactId>"));
    assertDependencyScope(kafka, "testara-streaming", null);
    assertDependencyScope(kafka, "testara-streaming-cucumber", "test");
    assertTrue(kafka.contains("Expected shape: kafka.service.{alias}.servers|groupId|topics.{topic}"));
    assertFalse(kafka.contains("Given user start kafka producer for orderStream"));

    String elastic = skill.execute(new TestInitSkill.Input("elastic", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "elastic-automation"),
        context());
    assertTrue(elastic.contains("<artifactId>testara-elastic</artifactId>"));
    assertTrue(elastic.contains("<artifactId>testara-elastic-cucumber</artifactId>"));
    assertDependencyScope(elastic, "testara-elastic", null);
    assertDependencyScope(elastic, "testara-elastic-cucumber", "test");
    assertTrue(elastic.contains("Expected shape: elasticsearch.service.{alias}.hosts[0]|username|password|secured|requireAuthentication"));
    assertFalse(elastic.contains("Given [elastic-search] connect to elastic search with name catalog"));
  }

  @Test
  void initAsksForCoordinatesBeforeUsingDefaults() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", null, "selenium", false),
        context());

    assertTrue(output.contains("needs_input: testara_init_coordinates"));
    assertTrue(output.contains("groupId"));
    assertTrue(output.contains("artifactId"));
    assertTrue(output.contains("next_step"));
    // option_auto removed — agent must ask user, not guess
    assertFalse(output.contains("autoGenerateCoordinates=true"));
    assertFalse(output.contains("<project xmlns="));
  }

  @Test
  void initRefusesToWriteIntoImplicitHomeRoot() {
    Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    AgentContext unsafe = new AgentContext(home, null, AgentMode.PATCH, null,
        Map.of("write", "true", "autoGenerateCoordinates", "true"));

    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", null, "selenium", false),
        unsafe);

    assertTrue(output.contains("needs_input: testara_init_project_root"));
    assertTrue(output.contains("projectRoot"));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of());
  }

  /** Context where engine has already been confirmed by the user — bypasses engine prompt. */
  private AgentContext confirmedEngineContext() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null,
        Map.of("engineConfirmed", "true"));
  }

  private void assertConfigurationPropertiesDoNotContainUserValues(String output) {
    String configuration = section(output, "## configuration.properties", "## cucumber.properties");
    assertFalse(configuration.contains("http://localhost"));
    assertFalse(configuration.contains("secret_sauce"));
    assertFalse(configuration.contains("config.consul."));
    assertFalse(configuration.contains("config.vault."));
    assertFalse(configuration.contains("api.sample-api"));
  }

  private String section(String output, String start, String end) {
    int s = output.indexOf(start);
    int e = output.indexOf(end);
    assertTrue(s >= 0 && e > s, "Expected section " + start);
    return output.substring(s, e);
  }

  private int countOccurrences(String text, String needle) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
  }

  private AgentContext autoContext() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null,
        Map.of("autoGenerateCoordinates", "true", "engineConfirmed", "true"));
  }

  private AgentContext examplesContext() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null,
        Map.of("includeExamples", "true", "engineConfirmed", "true"));
  }

  private void assertDependencyScope(String pomPreview, String artifactId, String expectedScope) {
    Pattern pattern = Pattern.compile("<dependency>\\s*<groupId>io\\.github\\.ygrip</groupId>\\s*<artifactId>"
        + Pattern.quote(artifactId)
        + "</artifactId>\\s*<version>\\$\\{testara\\.version}</version>(?:\\s*<scope>([^<]+)</scope>)?\\s*</dependency>",
        Pattern.DOTALL);
    var matcher = pattern.matcher(pomPreview);
    assertTrue(matcher.find(), "Missing dependency " + artifactId);
    String actualScope = matcher.group(1);
    if (expectedScope == null) {
      assertTrue(actualScope == null || actualScope.isBlank(), artifactId + " should use compile scope");
    } else {
      assertTrue(expectedScope.equals(actualScope), artifactId + " should use " + expectedScope + " scope");
    }
  }
}
