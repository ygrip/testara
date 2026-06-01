package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraGuideSkillTest {

  @Test
  void guideIncludesUtilityReuseAndConventionRules() {
    TestaraGuideSkill skill = new TestaraGuideSkill();
    String guide = skill.execute("utilities", context("markdown"));

    assertTrue(guide.contains("MapperHelper"));
    assertTrue(guide.contains("TransformerService"));
    assertTrue(guide.contains("CommandExecutor"));
    assertTrue(guide.contains("ValidatorHelper"));
    assertTrue(guide.contains("SqlHelper"));
    assertTrue(guide.contains("KafkaConsumerHelper"));
    assertTrue(guide.contains("ElasticSearchHelper"));

    String conventions = skill.execute("conventions", context("markdown"));
    assertTrue(conventions.contains("Do not generate helper classes that duplicate"));
  }

  @Test
  void guideUsesCurrentUiAndScanLocationConventions() {
    String guide = new TestaraGuideSkill().execute("all", context("markdown"));

    assertTrue(guide.contains("Given user using chrome in desktop"));
    assertTrue(guide.contains("class.loader.default-scan-locations=io.github.ygrip.testara,{basePackage}"));
    assertTrue(guide.contains("command.executor.scan-locations=io.github.ygrip.testara,{basePackage}.command"));
    assertTrue(guide.contains("validator.helper.scan-locations=io.github.ygrip.testara,{basePackage}.validation"));
    assertFalse(guide.contains("Given user using web in desktop"));
    assertFalse(guide.contains("{basePackage}.commands"));
    assertFalse(guide.contains("{basePackage}.validations"));
  }

  @Test
  void conciseGuideMentionsBuiltInReuse() {
    String guide = new TestaraGuideSkill().execute("all", context("concise"));

    assertTrue(guide.contains("## Agent quick guardrails"));
    assertTrue(guide.contains("Compile scope for Testara modules imported by `src/main/java`"));
    assertTrue(guide.contains("DataTables: ALL steps that pass key-value pairs MUST have `| key | value |`"));
    assertTrue(guide.contains("UserAction classes must be top-level classes"));
    assertTrue(guide.contains("Do not generate wrapper classes with nested `static class"));
    assertTrue(guide.contains("allowAnonymousCall = true"));
    assertTrue(guide.contains("## Utilities and helpers"));
    assertTrue(guide.contains("## Built-in step usage"));
    assertTrue(guide.contains("## POM dependency scope rules"));
    assertTrue(guide.contains("## Helper step command validation decisions"));
    assertTrue(guide.contains("## DB Kafka Elastic patterns"));
    assertTrue(guide.contains("Reuse Testara built-ins before creating project code"));
  }

  @Test
  void guideDocumentsDbKafkaElasticPatterns() {
    String guide = new TestaraGuideSkill().execute("DB Kafka Elastic", context("markdown"));

    assertTrue(guide.contains("sql.service.{alias}") || guide.contains("sql.service.{name}"));
    assertTrue(guide.contains("Given [mongo] connect to database with name {alias}"));
    assertTrue(guide.contains("Given user start kafka producer for {alias}"));
    assertTrue(guide.contains("Then [elastic-search] assign previous elastic search response to {alias}"));
  }

  @Test
  void guideDocumentsHelperStepCommandValidationDecisionRules() {
    String guide = new TestaraGuideSkill().execute("Helper step command validation", context("markdown"));

    assertTrue(guide.contains("Command: create a project command under `{basePackage}.command`"));
    assertTrue(guide.contains("Validation: create a project validation under `{basePackage}.validation`"));
    assertTrue(guide.contains("Custom Cucumber step: last resort"));
    assertTrue(guide.contains("Request spec: for API"));
    assertTrue(guide.contains("Page/action: for UI"));
  }

  @Test
  void guideDocumentsPomScopeRules() {
    String guide = new TestaraGuideSkill().execute("POM dependency scope", context("markdown"));

    assertTrue(guide.contains("Compile scope: any Testara module imported by Java under `src/main/java`"));
    assertTrue(guide.contains("UI slice: `testara-ui` and selected engine"));
    assertTrue(guide.contains("testara-ui-cucumber` test"));
    assertTrue(guide.contains("API slice: `testara-api` compile, `testara-api-cucumber` test"));
  }

  @Test
  void guideDocumentsCompileSafeUserActionApi() {
    String guide = new TestaraGuideSkill().execute("UserAction class", context("markdown"));

    assertTrue(guide.contains("`UserAction` does not expose `type()`, `click()`, `enter()"));
    assertTrue(guide.contains("import io.github.ygrip.testara.ui.page.NamedPage;"));
    assertTrue(guide.contains("Verified in Testara 2.0.5 artifacts"));
    assertTrue(guide.contains("Scroll.to(\"element name\").andAlignToTop()"));
    assertTrue(guide.contains("attemptsTo("));
    assertTrue(guide.contains("@OnPage(value = {LoginPage.class})"));
    assertFalse(guide.contains("@OnPage(\"login\")"));
  }

  @Test
  void guideStepsSectionUsesGeneratedBuiltInCatalog() {
    String guide = new TestaraGuideSkill().execute("steps", context("concise"));

    assertTrue(guide.contains("## Built-in Step Reference"));
    assertTrue(guide.contains("### ui"));
    assertTrue(guide.contains("Given {actor} using {word} in {devices}"));
    assertTrue(guide.contains("When {actor} type value {string} to {string} in the {string} page"));
    assertTrue(guide.contains("When {actor} click the {string} in the {string} page"));
    assertTrue(guide.contains("Then {actor} should see {string} is {displayedOrNotDisplayed}"));
    assertTrue(guide.contains("### api"));
    assertTrue(guide.contains("process request"));
  }

  private AgentContext context(String format) {
    return new AgentContext(Path.of("."), null, AgentMode.READ_ONLY, null, Map.of("format", format));
  }
}
