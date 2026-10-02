package io.github.ygrip.testara.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSummarySkillTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void emitsValidJsonAndIgnoresCopiedTargetFeatures(@TempDir Path root) throws Exception {
    String feature = """
        @api
        Feature: Summary

          @smoke
          Scenario: One
            Given a precondition
            Then it works
        """;
    Path source = root.resolve("src/test/resources/features/summary.feature");
    Path copied = root.resolve("target/test-classes/features/summary.feature");
    Files.createDirectories(source.getParent());
    Files.createDirectories(copied.getParent());
    Files.writeString(source, feature);
    Files.writeString(copied, feature);

    AgentContext context = new AgentContext(
        root, null, AgentMode.READ_ONLY, new DisabledLlmClient(),
        Map.of("format", "json"));

    String output = new TestSummarySkill().execute(
        new TestSummarySkill.Input(root, null), context);
    var json = MAPPER.readTree(output);

    assertEquals(1, json.path("featureFiles").asInt());
    assertEquals(1, json.path("scenarios").asInt());
    assertEquals("@smoke",
        json.path("features").path(0).path("scenarios").path(0).path("tags").path(0).asText());
  }

  @Test
  void markdownShowsRuleBackgroundOncePerRule(@TempDir Path root) throws Exception {
    Path source = root.resolve("src/test/resources/features/rules.feature");
    Files.createDirectories(source.getParent());
    Files.writeString(source, """
        Feature: Rules

          Rule: Logged in
            Background:
              Given user using chrome in desktop

            Scenario: One
              When user open "login" page

            Scenario: Two
              When user open "cart" page
        """);
    AgentContext context = new AgentContext(root, null, AgentMode.READ_ONLY, new DisabledLlmClient(), Map.of());

    String output = new TestSummarySkill().execute(new TestSummarySkill.Input(root, null), context);

    assertEquals(1, output.split("\\*\\*Rule background steps:\\*\\*", -1).length - 1, output);
    assertTrue(output.contains("- Given user using chrome in desktop"), output);
  }
}
