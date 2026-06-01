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

  @Test
  void recognizesSauceDemoUiBaseStepsAsTestaraFlavor() throws Exception {
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
            When user type value "properties(test.user.username)" to "username field" in the "login" page
            And user type value "properties(test.user.password)" to "password field" in the "login" page
            And user click the "button login" in the "login" page
            Then user should see "inventory list" is displayed
        """);

    String output = new TestReviewSkill().execute(
        Path.of("src/test/resources/features/login.feature"), context());

    assertTrue(output.contains("## Testara Flavor Score: 100%"));
    assertTrue(output.contains("| Total steps | 7 |"));
    assertTrue(output.contains("| Built-in Testara steps | 7 |"));
    assertFalse(output.contains("Flavor score below 80%"));
  }

  @Test
  void unmatchedGenericUiStepStillGetsMigrationSuggestion() throws Exception {
    Path feature = projectRoot.resolve("src/test/resources/features/login.feature");
    Files.createDirectories(feature.getParent());
    Files.writeString(feature, """
        @ui @login
        Feature: Login

          @P1
          Scenario: User logs in
            When I click the login button
            Then user should see "inventory list" is displayed
        """);

    String output = new TestReviewSkill().execute(
        Path.of("src/test/resources/features/login.feature"), context());

    assertTrue(output.contains("MIGRATABLE: generic step where Testara built-in exists"));
    assertTrue(output.contains("could be replaced with a Testara built-in step"));
    assertTrue(output.contains("| Migratable (generic where built-in exists) | 1 |"));
  }

  private AgentContext context() {
    TestaraProjectProfile profile = new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of());
  }
}
