package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

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
        context());

    assertTrue(output.contains("<testara.version>2.0.4</testara.version>"));
    assertFalse(output.contains("<testara.version>2.0.1</testara.version>"));
  }

  @Test
  void explicitAgentVersionTakesPrecedence() {
    System.setProperty("testara.agent.version", "9.8.7");

    String output = new TestInitSkill().execute(
        new TestInitSkill.Input("ui", "io.github.ygrip.sample", "selenium", false),
        context());

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
    assertTrue(output.contains("<artifactId>testara-junit5</artifactId>"));
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

    String sql = skill.execute(new TestInitSkill.Input("sql", "io.github.ygrip.automation", "selenium", false),
        context());
    assertTrue(sql.contains("<artifactId>testara-database-cucumber</artifactId>"));
    assertTrue(sql.contains("sql.service.settlementDb.uri=properties(db.settlement.uri)"));

    String kafka = skill.execute(new TestInitSkill.Input("kafka", "io.github.ygrip.automation", "selenium", false),
        context());
    assertTrue(kafka.contains("<artifactId>testara-streaming-cucumber</artifactId>"));
    assertTrue(kafka.contains("kafka.service.orderStream.servers=properties(kafka.order.servers)"));
    assertTrue(kafka.contains("Given user start kafka producer for orderStream"));

    String elastic = skill.execute(new TestInitSkill.Input("elastic", "io.github.ygrip.automation", "selenium", false),
        context());
    assertTrue(elastic.contains("<artifactId>testara-elastic-cucumber</artifactId>"));
    assertTrue(elastic.contains("elasticsearch.service.catalog.hosts[0]=properties(elasticsearch.catalog.host)"));
    assertTrue(elastic.contains("Given [elastic-search] connect to elastic search with name catalog"));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of());
  }
}
