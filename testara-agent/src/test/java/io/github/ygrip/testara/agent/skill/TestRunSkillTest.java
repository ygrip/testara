package io.github.ygrip.testara.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Test
  void readOnlyModeCannotExecuteTests() {
    AgentContext readOnly = new AgentContext(projectRoot, profileWithSaucedemo(),
        AgentMode.READ_ONLY, null, Map.of("dryRun", "false", "execute", "true"));

    String output = new TestRunSkill().execute("run @smoke", readOnly);

    assertTrue(output.contains("Execution blocked"));
    assertTrue(output.contains("Agent mode"));
  }

  @Test
  void staleReportFromEarlierRunIsIgnoredWhenBuildFails() throws IOException {
    Path staleReport = projectRoot.resolve("target/destination/cucumber.json");
    Files.createDirectories(staleReport.getParent());
    Files.writeString(staleReport, PASSING_REPORT);
    Files.setLastModifiedTime(staleReport, FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS)));
    fakeLauncher("mvnw", """
        echo "[ERROR] COMPILATION ERROR : cannot find symbol"
        exit 1
        """);

    String output = new TestRunSkill().execute("run @smoke", executeContext(profileWithSaucedemo()));

    assertTrue(output.contains("**Status:** FAILED"), output);
    assertFalse(output.contains("## Test Run Report"), "stale cucumber.json must not be reported: " + output);
    assertTrue(output.contains("report missing"), output);
  }

  @Test
  void nonZeroExitFailsTheRunEvenWhenFreshReportPasses() throws IOException {
    Path fixture = projectRoot.resolve("passing.json");
    Files.writeString(fixture, PASSING_REPORT);
    fakeLauncher("mvnw", """
        mkdir -p target/destination
        cp passing.json target/destination/cucumber.json
        exit 1
        """);

    String output = new TestRunSkill().execute("run @smoke", executeContext(profileWithSaucedemo()));

    assertTrue(output.contains("## Test Run Report"), output);
    assertTrue(output.contains("- Passed: 1"), output);
    assertFalse(output.contains("**Status:** PASSED"), output);
    assertTrue(output.contains("**Status:** FAILED"), output);
  }

  @Test
  void cleanExitWithAnEmptyFreshReportFailsWhenPreflightMatchedScenarios() throws IOException {
    fakeLauncher("mvnw", """
        mkdir -p target/destination
        echo "[]" > target/destination/cucumber.json
        exit 0
        """);

    String output = new TestRunSkill().execute("run @smoke", executeContext(profileWithSaucedemo()));

    assertTrue(output.contains("- Total: 0"), output);
    assertFalse(output.contains("**Status:** PASSED"), output);
    assertTrue(output.contains("**Status:** FAILED"), output);
    assertTrue(output.contains("no scenarios executed"), output);
    assertEquals(1, TestRunSkill.exitCode(output));
  }

  @Test
  void jsonFormatReturnsMachineReadableReport() throws IOException {
    Path fixture = projectRoot.resolve("passing.json");
    Files.writeString(fixture, PASSING_REPORT);
    fakeLauncher("mvnw", """
        mkdir -p target/destination
        cp passing.json target/destination/cucumber.json
        exit 0
        """);

    String output = new TestRunSkill().execute("run @smoke", new AgentContext(projectRoot, profileWithSaucedemo(),
        AgentMode.APPLY, null, Map.of("dryRun", "false", "execute", "true", "format", "json")));

    JsonNode json = new ObjectMapper().readTree(output);
    assertEquals("PASSED", json.path("status").asText());
    assertEquals(1, json.path("passed").asInt());
    assertTrue(json.path("reportFile").asText().endsWith("cucumber.json"));
    assertTrue(json.path("logFile").asText().endsWith(".log"));
  }

  @Test
  void gradleProjectRunsGradleWithAgentInitScript() throws IOException {
    Files.writeString(projectRoot.resolve("build.gradle.kts"), "plugins { java }\n");
    Path fixture = projectRoot.resolve("passing.json");
    Files.writeString(fixture, PASSING_REPORT);
    fakeLauncher("gradlew", """
        echo "args: $@"
        mkdir -p build
        cp passing.json build/cucumber.json
        echo "BUILD SUCCESSFUL in 3s"
        exit 0
        """);

    String output = new TestRunSkill().execute("run @smoke",
        executeContext(profileWithSaucedemo(BuildTool.GRADLE)));

    Path initScript = projectRoot.resolve(".testara-agent/gradle/testara-cucumber.init.gradle");
    assertTrue(Files.isRegularFile(initScript), output);
    assertTrue(output.contains("test --console=plain --no-daemon --init-script " + initScript), output);
    assertTrue(output.contains("-Pcucumber.filter.tags=@smoke"), output);
    assertTrue(output.contains("**Status:** PASSED"), output);
    assertTrue(output.contains("- Passed: 1"), output);
    assertFalse(output.contains("mvn"), output);
  }

  @Test
  void gradleDryRunUsesModuleTaskPath() {
    String output = new TestRunSkill().execute("run @smoke", new AgentContext(projectRoot,
        profileWithSaucedemo(BuildTool.GRADLE), AgentMode.READ_ONLY, null,
        Map.of("dryRun", "true", "module", "modules/api", "gradleTask", "integrationTest")));

    assertTrue(output.contains(":modules:api:integrationTest --console=plain"), output);
    assertTrue(output.contains("-Pcucumber.filter.tags=\"@smoke\""), output);
  }

  @Test
  void acceptsNestedModulePathAndRejectsTraversalWithMessage() {
    String nested = new TestRunSkill().execute("run @smoke", new AgentContext(projectRoot,
        profileWithSaucedemo(), AgentMode.READ_ONLY, null, Map.of("dryRun", "true", "module", "modules/api")));
    String traversal = new TestRunSkill().execute("run @smoke", new AgentContext(projectRoot,
        profileWithSaucedemo(), AgentMode.READ_ONLY, null, Map.of("dryRun", "true", "module", "../other")));

    assertTrue(nested.contains("mvn -pl modules/api verify"), nested);
    assertTrue(traversal.contains("Invalid test-run option"), traversal);
    assertTrue(traversal.contains("../other"), traversal);
  }

  @Test
  void rerunFailedHonorsModuleRerunFile() throws IOException {
    Path rerunFile = projectRoot.resolve("modules/api/target/rerun/rerun.txt");
    Files.createDirectories(rerunFile.getParent());
    Files.writeString(rerunFile, "src/test/resources/features/login.feature:12\n");

    String output = new TestRunSkill().execute("rerun", new AgentContext(projectRoot, emptyProfile(),
        AgentMode.READ_ONLY, null, Map.of("dryRun", "true", "rerunFailed", "true", "module", "modules/api")));

    assertTrue(output.contains("-Dcucumber.features=@" + rerunFile), output);
    assertTrue(output.contains("-pl modules/api"), output);
  }

  @Test
  void logAnalysisIgnoresZeroFailureCounters() {
    TestRunSkill.LogFacts facts = TestRunSkill.analyzeLog(List.of(
        "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0",
        "[INFO] BUILD SUCCESS",
        "[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0",
        "java.lang.IllegalStateException: boom"));

    assertEquals(List.of("line 3: [ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0",
        "line 4: java.lang.IllegalStateException: boom"), facts.errors());
    assertEquals("[INFO] BUILD SUCCESS", facts.buildSummary());
  }

  private void fakeLauncher(String name, String body) throws IOException {
    Path launcher = projectRoot.resolve(name);
    Files.writeString(launcher, "#!/bin/sh\n" + body);
    assertTrue(launcher.toFile().setExecutable(true));
  }

  private static final String PASSING_REPORT = """
      [{"uri": "features/e2e.feature", "elements": [
        {"type": "scenario", "keyword": "Scenario", "name": "Complete purchase flow",
         "steps": [{"result": {"status": "passed"}}]}]}]
      """;

  private AgentContext executeContext(TestaraProjectProfile profile) {
    return new AgentContext(projectRoot, profile, AgentMode.APPLY, null,
        Map.of("dryRun", "false", "execute", "true"));
  }

  private AgentContext context(TestaraProjectProfile profile) {
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("dryRun", "true"));
  }

  private TestaraProjectProfile profileWithSaucedemo() {
    return profileWithSaucedemo(BuildTool.MAVEN);
  }

  private TestaraProjectProfile profileWithSaucedemo(BuildTool buildTool) {
    ScenarioIndex purchase = new ScenarioIndex("Complete purchase flow - login, add to cart, and checkout",
        ScenarioType.SCENARIO, List.of("@P1", "@positive", "@smoke"), List.of(), List.of());
    FeatureIndex feature = new FeatureIndex(projectRoot.resolve("src/test/resources/features/saucedemo/e2e.feature"),
        "SauceDemo E2E - Login, Add to Cart, and Checkout",
        List.of("@ui", "@saucedemo", "@regression"), List.of(purchase), List.of());
    return new TestaraProjectProfile(projectRoot, buildTool, "21", List.of(),
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
