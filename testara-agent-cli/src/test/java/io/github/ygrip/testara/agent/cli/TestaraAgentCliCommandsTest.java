package io.github.ygrip.testara.agent.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.agent.skill.TestRunSkill;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraAgentCliCommandsTest {

  private static final String FEATURES = "src/test/resources/features";

  @TempDir
  Path project;

  @BeforeEach
  void createProject() throws Exception {
    Files.writeString(project.resolve("pom.xml"), "<project/>");
    Path features = Files.createDirectories(project.resolve(FEATURES));
    Files.writeString(features.resolve("login.feature"), """
        @smoke
        Feature: Login

          Scenario: user logs in
            Given user open "login" page
            Then user should see "welcome" is displayed
        """);
  }

  @Test
  void summaryAndReviewResolveTheTargetAgainstTheProjectRoot() throws Exception {
    Result summary = run("test-summary", "--project", project.toString(), FEATURES);
    Result review = run("test-review", "--project", project.toString(), FEATURES);

    assertEquals(0, summary.exitCode(), summary.output());
    assertTrue(summary.output().contains("Login"), summary.output());
    assertEquals(0, review.exitCode(), review.output());
    assertFalse(review.output().contains("No feature files found"), review.output());
    assertFalse(Files.exists(project.resolve(FEATURES).resolve(".testara-agent")));
  }

  @Test
  void summaryOfAMissingTargetIsBlocked() throws Exception {
    Result summary = run("test-summary", "--project", project.toString(), "src/test/resources/none");

    assertEquals(2, summary.exitCode(), summary.output());
  }

  @Test
  void testRunExitCodes() throws Exception {
    Result plan = run("test-run", "@smoke", "--project", project.toString());
    Result preflight = run("test-run", "@missing", "--project", project.toString());
    Result invalid = run("test-run", "@smoke", "--timeout-minutes", "0", "--project", project.toString());

    assertEquals(0, plan.exitCode(), plan.output());
    assertEquals(1, preflight.exitCode(), preflight.output());
    assertTrue(preflight.output().startsWith("preflight: FAILED"), preflight.output());
    assertEquals(2, invalid.exitCode(), invalid.output());
  }

  @Test
  void testRunExitCodeFollowsTheRunVerdict() {
    assertEquals(1, TestRunSkill.exitCode("{\"status\":\"FAILED\",\"durationMs\":1}"));
    assertEquals(1, TestRunSkill.exitCode("## Test Run Log Summary\n\n**Status:** TIMEOUT  \n"));
    assertEquals(0, TestRunSkill.exitCode("{\"status\":\"PASSED\",\"durationMs\":1}"));
    assertEquals(2, TestRunSkill.exitCode("## Test Run Plan\n\n> **Execution blocked.** Set X=true\n"));
    assertEquals(2, TestRunSkill.exitCode("needs_input: test_run_filter\n"));
  }

  @Test
  void dryRunWinsOverExecute() throws Exception {
    Result both = run("test-run", "@smoke", "--execute", "--dry-run", "--project", project.toString());
    Result dryRunFalse = run("test-run", "@smoke", "--dry-run=false", "--project", project.toString());

    assertEquals(0, both.exitCode(), both.output());
    assertTrue(both.output().contains("## Test Run Plan"), both.output());
    assertFalse(both.output().contains("Test Run Log Summary"), both.output());
    assertTrue(dryRunFalse.output().contains("## Test Run Plan"), dryRunFalse.output());
    assertFalse(dryRunFalse.output().contains("Test Run Log Summary"), dryRunFalse.output());
  }

  @Test
  void planInfersTheSliceWhenNotGiven() throws Exception {
    Result inferred = run("test-plan", "verify login page shows error message", "--project", project.toString());

    assertTrue(inferred.output().contains("@ui") || inferred.output().contains("test_plan_ui_context"),
        inferred.output());
    assertFalse(inferred.output().contains("@api"), inferred.output());
  }

  @Test
  void yamlWriteDisabledBlocksTheWriteFlag() throws Exception {
    Files.writeString(project.resolve("testara-agent.yaml"), "write:\n  enabled: false\n");

    Result result = run("testara-api", "--mode", "request-spec", "--domain", "refund", "--flow", "approve-refund",
        "--method", "POST", "--endpoint", "/refunds", "--write", "--project", project.toString());

    assertEquals(2, result.exitCode(), result.output());
    assertTrue(result.output().startsWith("write_disabled: write.enabled: false"), result.output());
    assertFalse(Files.exists(project.resolve("src/test/resources/files")));
  }

  @Test
  void skillSubcommandsAreRegistered() throws Exception {
    Result guide = run("testara-guide", "properties");
    Result db = run("testara-db", "--slice", "sql", "--mode", "config", "--name", "settlement");
    Result context = run("testara-context", project.toString());

    assertEquals(0, guide.exitCode(), guide.output());
    assertFalse(guide.output().isBlank());
    assertEquals(0, db.exitCode(), db.output());
    assertTrue(db.output().contains("sql.service."), db.output());
    assertEquals(0, context.exitCode(), context.output());
    for (String name : new String[] {"testara-property", "testara-ui"}) {
      assertEquals(0, run(name, "--help").exitCode(), name);
    }
  }

  @Test
  void generatedCommandDefaultsToTheProjectPackage() throws Exception {
    Result result = run("test-command", "generate customer code", "--project", project.toString());

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("package io.github.ygrip.automation.command;"), result.output());
  }

  private Result run(String... args) {
    PrintStream original = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int exitCode;
    try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
      System.setOut(capture);
      exitCode = new CommandLine(new TestaraAgentCli()).execute(args);
    } finally {
      System.setOut(original);
    }
    return new Result(exitCode, buffer.toString(StandardCharsets.UTF_8));
  }

  private record Result(int exitCode, String output) {}
}
