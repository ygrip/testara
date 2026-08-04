package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.ScenarioType;
import io.github.ygrip.testara.agent.index.TagIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void resolvesScenarioTextToNarrowTagExpression() {
    String output = new TestRunSkill().execute("run complete purchase flow", context(profileWithSaucedemo()));

    assertTrue(output.contains("**Resolved tag expression:** `@ui and @saucedemo and @regression and @smoke`"));
    assertTrue(output.contains("**Matched scenarios:** 1"));
    assertFalse(output.contains(" or "));
    assertFalse(output.contains("needs_input"));
  }

  @Test
  void keepsExplicitTagInputDirect() {
    String output = new TestRunSkill().execute("run @smoke", context(profileWithSaucedemo()));

    assertTrue(output.contains("**Resolved tag expression:** `@smoke`"));
    assertTrue(output.contains("**Matched scenarios:** 1"));
  }

  @Test
  void preflightFailsWithSuggestionsWhenTagMatchesNoScenarios() {
    String output = new TestRunSkill().execute("run @nonexistent", context(profileWithSaucedemo()));

    assertTrue(output.contains("preflight: FAILED"));
    assertTrue(output.contains("mavenExecuted: false"));
    assertTrue(output.contains("reason:"));
    assertTrue(output.contains("resolved-expression: @nonexistent"));
    assertTrue(output.contains("available-tags:"));
    assertFalse(output.contains("**Matched scenarios:**"));
    assertFalse(output.contains("needs_input"));
  }

  @Test
  void preflightSuggestsRelatedTagsForPartialMatch() {
    String output = new TestRunSkill().execute("run @smok", context(profileWithSaucedemo()));

    assertTrue(output.contains("preflight: FAILED"));
    assertTrue(output.contains("mavenExecuted: false"));
    assertTrue(output.contains("suggested-expressions:"));
    assertTrue(output.contains("@smoke"));
  }

  @Test
  void asksForInputWhenProjectHasNoRunnableContext() {
    String output = new TestRunSkill().execute("run checkout", context(emptyProfile()));

    assertTrue(output.contains("needs_input: test_run_filter"));
    assertTrue(output.contains("feature name, or scenario name"));
    assertTrue(output.contains("indexed-features: 0 | indexed-scenarios: 0"));
  }

  @Test
  void executedRunCapturesMavenLogAndReturnsSummary() throws IOException {
    Path mvnw = projectRoot.resolve("mvnw");
    Files.writeString(mvnw, """
        #!/bin/sh
        echo "[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0"
        echo "1 scenarios (1 failed)"
        echo "3 steps (1 failed, 2 passed)"
        echo "src/test/resources/features/login.feature:12 expected error"
        echo "[INFO] BUILD FAILURE"
        echo "[INFO] Total time:  2.345 s"
        exit 1
        """);
    assertTrue(mvnw.toFile().setExecutable(true));

    String output = new TestRunSkill().execute("run @smoke", executeContext(profileWithSaucedemo()));

    assertTrue(output.contains("## Test Run Log Summary"));
    assertTrue(output.contains("**Exit code:** 1"));
    assertTrue(output.contains("**Maven command:** `"));
    assertTrue(output.contains("**Log file:** `"));
    assertTrue(output.contains("1 scenarios (1 failed)"));
    assertTrue(output.contains("src/test/resources/features/login.feature:12"));
    assertFalse(output.contains("needs_input"));
  }

  @Test
  void rerunFailedUsesNativeRerunFileFeaturePathNotTagFilter() throws IOException {
    Path rerunFile = projectRoot.resolve("target/rerun/rerun.txt");
    Files.createDirectories(rerunFile.getParent());
    Files.writeString(rerunFile, "src/test/resources/features/login.feature:12\n");

    Map<String, String> opts = Map.of("dryRun", "true", "rerunFailed", "true");
    String output = new TestRunSkill().execute("rerun", new AgentContext(projectRoot,
        emptyProfile(), AgentMode.READ_ONLY, null, opts));

    assertTrue(output.contains("-Dcucumber.features=@" + rerunFile),
        "rerun must use Cucumber's native @<rerun-file> feature-path, not -Dcucumber.filter.tags");
    assertFalse(output.contains("-Dcucumber.filter.tags"));
  }

  @Test
  void rerunFailedReportsClearlyWhenNoRerunFileExists() {
    Map<String, String> opts = Map.of("dryRun", "true", "rerunFailed", "true");
    String output = new TestRunSkill().execute("rerun", new AgentContext(projectRoot,
        emptyProfile(), AgentMode.READ_ONLY, null, opts));

    assertTrue(output.contains("No previous test failures found"));
    assertTrue(output.contains("target/rerun/rerun.txt"));
  }

  private AgentContext executeContext(TestaraProjectProfile profile) {
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null,
        Map.of("dryRun", "false", "execute", "true"));
  }

  private AgentContext context(TestaraProjectProfile profile) {
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("dryRun", "true"));
  }

  private TestaraProjectProfile profileWithSaucedemo() {
    ScenarioIndex purchase = new ScenarioIndex("Complete purchase flow - login, add to cart, and checkout",
        ScenarioType.SCENARIO, List.of("@P1", "@positive", "@smoke"), List.of(), List.of());
    FeatureIndex feature = new FeatureIndex(projectRoot.resolve("src/test/resources/features/saucedemo/e2e.feature"),
        "SauceDemo E2E - Login, Add to Cart, and Checkout",
        List.of("@ui", "@saucedemo", "@regression"), List.of(purchase), List.of());
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(projectRoot.resolve("src/test/resources/features")), List.of(), List.of(),
        List.of(feature), List.of(), List.of(), List.of(), List.of(),
        List.of(new TagIndex("@ui", 1, 1, List.of(feature.path()), List.of(purchase.name())),
            new TagIndex("@saucedemo", 1, 1, List.of(feature.path()), List.of(purchase.name())),
            new TagIndex("@regression", 1, 1, List.of(feature.path()), List.of(purchase.name())),
            new TagIndex("@smoke", 1, 1, List.of(feature.path()), List.of(purchase.name()))),
        Map.of(), Map.of(), List.of(), List.of());
  }

  private TestaraProjectProfile emptyProfile() {
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
  }
}
