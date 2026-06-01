package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlanSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void loginUiPlanUsesExecutableTestaraStepsAndReusableAction() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("login to saucedemo and land on inventory", "ui", "saucedemo", List.of()),
        context());

    assertTrue(output.contains("Given user using chrome in desktop"));
    assertTrue(output.contains("When user open \"login\" page"));
    assertTrue(output.contains("When user do \"login with credentials\" in \"login\" page with parameter"));
    assertTrue(output.contains("Then user is in \"inventory\" page"));
    assertTrue(output.contains("Then user should see \"error message\" is displayed"));
    assertFalse(output.contains("# MISSING"));
    assertFalse(output.contains("user using web in desktop"));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, profile(), AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }

  private TestaraProjectProfile profile() {
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
  }
}
