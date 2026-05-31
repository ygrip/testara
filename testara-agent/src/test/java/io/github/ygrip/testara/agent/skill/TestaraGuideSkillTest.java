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

    assertTrue(guide.contains("## Utilities and helpers"));
    assertTrue(guide.contains("## Built-in step usage"));
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

  private AgentContext context(String format) {
    return new AgentContext(Path.of("."), null, AgentMode.READ_ONLY, null, Map.of("format", format));
  }
}
