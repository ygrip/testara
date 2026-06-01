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

class TestaraDebugSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void classifiesUnmatchedStepText() {
    String output = new TestaraDebugSkill().execute(new TestaraDebugSkill.Input("",
        "Then user should see \"complete header\" with text \"Thank you for your order!\""), context());

    assertTrue(output.contains("likely_cause: unmatched_step_text"));
    assertTrue(output.contains("step_link: unmatched"));
    assertTrue(output.contains("testara_guide section=steps"));
  }

  @Test
  void classifiesSelectorFailureForMatchedStep() {
    String output = new TestaraDebugSkill().execute(new TestaraDebugSkill.Input("""
        org.openqa.selenium.NoSuchElementException: Unable to locate element
        """, "Then user should see \"error message\" is displayed"), context());

    assertTrue(output.contains("step_link: BUILT_IN"));
    assertTrue(output.contains("likely_cause: selector_or_visibility"));
    assertTrue(output.contains("testara_ui mode=validate-page"));
  }

  @Test
  void classifiesRunnerAndJavaMismatch() {
    String output = new TestaraDebugSkill().execute(new TestaraDebugSkill.Input(
        "UnsupportedClassVersionError: testara-reporter-plugin requires Java 21", null), context());

    assertTrue(output.contains("likely_cause: runner_or_java_configuration"));
    assertTrue(output.contains("mvn -version uses Java 21+"));
  }

  private AgentContext context() {
    TestaraProjectProfile profile = new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }
}
