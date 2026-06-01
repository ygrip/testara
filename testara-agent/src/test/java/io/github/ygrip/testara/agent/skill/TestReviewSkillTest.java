package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReviewSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void resolvesRelativePathFromProjectRoot() throws Exception {
    Path feature = projectRoot.resolve("src/test/resources/features/login.feature");
    Files.createDirectories(feature.getParent());
    Files.writeString(feature, """
        @ui @login
        Feature: Login

          @P1
          Scenario: User logs in
            Given user using chrome in desktop
            When user open "login" page
            Then user is in "login" page
        """);

    String output = new TestReviewSkill().execute(
        Path.of("src/test/resources/features/login.feature"), context());

    assertTrue(output.contains("Feature files reviewed:"));
    assertFalse(output.contains("No feature files found"));
  }

  private AgentContext context() {
    TestaraProjectProfile profile = new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of());
  }
}
