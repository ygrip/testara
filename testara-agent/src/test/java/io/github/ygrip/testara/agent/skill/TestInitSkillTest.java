package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

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

    assertTrue(output.contains("<testara.version>2.0.4</testara.version>"));
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
    assertTrue(output.contains("package com.acme.qa.checkouttests.runner;"));
    assertTrue(output.contains("@TestSuite"));
  }

  @Test
  void apiPreviewUsesTestaraRequestSpecStructure() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", "io.github.ygrip.example", "selenium", false,
            "io.github.ygrip", "api-automation"),
        context());

    assertTrue(output.contains("<artifactId>testara-api-cucumber</artifactId>"));
    assertDependencyScope(output, "testara-api", null);
    assertDependencyScope(output, "testara-api-cucumber", "test");
    assertDependencyScope(output, "testara-command", null);
    assertDependencyScope(output, "testara-validation", null);
    assertTrue(output.contains("<artifactId>testara-junit5</artifactId>"));
    assertDependencyScope(output, "testara-junit5", "test");
    assertFalse(output.contains("<artifactId>junit-platform-suite</artifactId>"));
    assertTrue(output.contains("<artifactId>maven-failsafe-plugin</artifactId>"));
    assertTrue(output.contains("<goal>integration-test</goal>"));
    assertTrue(output.contains("<goal>verify</goal>"));
    assertFalse(output.contains("<artifactId>maven-surefire-plugin</artifactId>"));
    assertTrue(output.contains("import io.github.ygrip.testara.engine.suites.TestSuite;"));
    assertFalse(output.contains("@IncludeEngines(\"cucumber\")"));
    assertTrue(output.contains("class.loader.default-scan-locations=io.github.ygrip.testara,io.github.ygrip.example"));
    assertTrue(output.contains("api.service.sample-api.host=properties(api.sample-api.host)"));
    assertTrue(output.contains("api.sample-api.host=http://localhost:8080"));
    assertTrue(output.contains("## cucumber.properties"));
    assertTrue(output.contains("## junit-platform.properties"));
    assertTrue(output.contains("cucumber.object-factory=io.github.ygrip.testara.engine.factory.TestaraCucumberObjectFactory"));
    assertTrue(output.contains("cucumber.filter.tags=(@api or @sample) and not (@manual or @deprecated or @ignored)"));
    assertTrue(output.contains("Given [api] using service with alias sample-api"));
    assertTrue(output.contains("When [api] process request to \"files/sample/request/sample-get\""));
    assertTrue(output.contains("Then [api] response statusCode should be 200"));
  }

  @Test
  void seleniumUiPreviewUsesExplicitUiDependenciesAndScanLocations() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("ui", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "ui-automation"),
        context());

    assertTrue(output.contains("<artifactId>testara-ui</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-cucumber</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-selenium</artifactId>"));
    assertDependencyScope(output, "testara-ui", null);
    assertDependencyScope(output, "testara-ui-selenium", null);
    assertDependencyScope(output, "testara-ui-cucumber", "test");
    assertTrue(output.contains("selenium.driver.page-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("public class HomePage extends SeleniumPage"));
    assertTrue(output.contains("private static final Locator SEARCH_INPUT"));
  }

  @Test
  void uiPreviewUsesExampleProjectConventions() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("ui", "io.github.ygrip.automation", "playwright", false,
            "io.github.ygrip", "ui-automation"),
        context());

    assertTrue(output.contains("<artifactId>testara-ui</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-cucumber</artifactId>"));
    assertTrue(output.contains("<artifactId>testara-ui-playwright</artifactId>"));
    assertDependencyScope(output, "testara-ui-playwright", null);
    assertTrue(output.contains("class.loader.default-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("playwright.browser.page-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("web.page.desktop.home.url=properties(app.web.home-url)"));
    assertTrue(output.contains("app.web.home-url=http://localhost:3000"));
    assertTrue(output.contains("Given user using chrome in desktop"));
    assertTrue(output.contains("Then user is in \"home\" page"));
    assertFalse(output.contains("user using web in desktop"));
    assertFalse(output.contains("io.github.ygrip.testara.ui.page.PageContext"));
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
    assertTrue(sql.contains("sql.service.settlementDb.uri=properties(db.settlement.uri)"));

    String kafka = skill.execute(new TestInitSkill.Input("kafka", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "kafka-automation"),
        context());
    assertTrue(kafka.contains("<artifactId>testara-streaming</artifactId>"));
    assertTrue(kafka.contains("<artifactId>testara-streaming-cucumber</artifactId>"));
    assertDependencyScope(kafka, "testara-streaming", null);
    assertDependencyScope(kafka, "testara-streaming-cucumber", "test");
    assertTrue(kafka.contains("kafka.service.orderStream.servers=properties(kafka.order.servers)"));
    assertTrue(kafka.contains("Given user start kafka producer for orderStream"));

    String elastic = skill.execute(new TestInitSkill.Input("elastic", "io.github.ygrip.automation", "selenium", false,
            "io.github.ygrip", "elastic-automation"),
        context());
    assertTrue(elastic.contains("<artifactId>testara-elastic</artifactId>"));
    assertTrue(elastic.contains("<artifactId>testara-elastic-cucumber</artifactId>"));
    assertDependencyScope(elastic, "testara-elastic", null);
    assertDependencyScope(elastic, "testara-elastic-cucumber", "test");
    assertTrue(elastic.contains("elasticsearch.service.catalog.hosts[0]=properties(elasticsearch.catalog.host)"));
    assertTrue(elastic.contains("Given [elastic-search] connect to elastic search with name catalog"));
  }

  @Test
  void initAsksForCoordinatesBeforeUsingDefaults() {
    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("api", null, "selenium", false),
        context());

    assertTrue(output.contains("needs_input: testara_init_coordinates"));
    assertTrue(output.contains("groupId"));
    assertTrue(output.contains("artifactId"));
    assertTrue(output.contains("autoGenerateCoordinates=true"));
    assertFalse(output.contains("<project xmlns="));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of());
  }

  private AgentContext autoContext() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null,
        Map.of("autoGenerateCoordinates", "true"));
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
