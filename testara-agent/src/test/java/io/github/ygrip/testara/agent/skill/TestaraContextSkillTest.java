package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraContextSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void conciseContextIncludesCatalogCountsAndTopStepPatterns() {
    String output = new TestaraContextSkill().execute(null, context("concise"));

    assertTrue(output.contains("flavor-steps:"));
    assertTrue(output.contains("step-counts:"));
    assertTrue(output.contains("top-step-patterns:"));
    assertTrue(output.contains("[ui] Given {actor} using {word} in {devices}"));
    assertTrue(output.contains("[ui] When {actor} type value {string} to {string} in the {string} page"));
  }

  @Test
  void fullContextPointsToStepsGuideForGroupedCatalog() {
    String output = new TestaraContextSkill().execute(null, context("markdown"));

    assertTrue(output.contains("## Built-in Step Reference (sample)"));
    assertTrue(output.contains("Use `testara_guide section=steps`"));
  }

  private AgentContext context(String format) {
    TestaraProjectProfile profile = new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("format", format));
  }
}
