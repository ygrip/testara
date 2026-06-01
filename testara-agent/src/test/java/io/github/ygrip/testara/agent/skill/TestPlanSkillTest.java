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
  void loginUiPlanUsesExecutableUiBaseSteps() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("login to saucedemo and land on inventory", "ui", "saucedemo", List.of()),
        context());

    assertTrue(output.contains("Given user using chrome in desktop"));
    assertTrue(output.contains("When user open \"login\" page"));
    // RULE 3: 3+ ops → user do "..." in "..." page with parameter + | key | value | DataTable
    assertTrue(output.contains("When user do \"login with credentials\" in \"login\" page with parameter"));
    assertTrue(output.contains("|key|value|"));
    assertTrue(output.contains("| username"));
    assertTrue(output.contains("| password"));
    assertTrue(output.contains("properties(test.user.username)"));
    assertTrue(output.contains("properties(test.user.password)"));
    // Must NOT fall back to individual type/click steps
    assertFalse(output.contains("user type value \"properties(test.user.username)\" to \"username field\""));
    assertFalse(output.contains("user click the \"button login\""));
    assertTrue(output.contains("Then user is in \"inventory\" page"));
    assertTrue(output.contains("Then user should see \"success message\" is displayed"));
    assertTrue(output.contains("Then user should see \"error message\" is displayed"));
    assertFalse(output.contains("# MISSING"));
    assertFalse(output.contains("user using web in desktop"));
  }

  @Test
  void vaguePlanAsksForClarifyingInput() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("test the app", "ui", null, List.of()),
        context());

    assertTrue(output.contains("needs_input: test_plan_ui_context"));
    assertTrue(output.contains("ask_user:"));
    assertTrue(output.contains("page"));
    assertTrue(output.contains("expected outcome"));
    assertTrue(output.contains("hint: call testara_plan again"));
    assertFalse(output.contains("Feature:"));
  }

  @Test
  void completelyBlankPlanAsksForSliceAndDomain() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("test", null, null, List.of()),
        context());

    assertTrue(output.contains("needs_input: test_plan_clarity"));
    assertTrue(output.contains("ask_user:"));
    assertTrue(output.contains("slice"));
    assertTrue(output.contains("domain"));
    assertFalse(output.contains("Feature:"));
  }

  @Test
  void apiPlanWithNoContextAsksForServiceAndEndpoint() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("verify the backend flow works correctly", "api", null, List.of()),
        context());

    assertTrue(output.contains("needs_input: test_plan_api_context"));
    assertTrue(output.contains("service alias"));
    assertTrue(output.contains("HTTP method"));
    assertFalse(output.contains("Feature:"));
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
