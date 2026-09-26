package io.github.ygrip.testara.agent.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.ygrip.testara.agent.skill.run.TestRunReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-all.7:
 * - parseCucumberJson counted every step status as if it were a scenario, and its "failed
 *   scenario" regex could never match real cucumber.json (status only appears per-step there).
 * - parseJunitXml used a proximity heuristic (substring + 2000-char window) that could attribute
 *   a later testcase's failure to an earlier, actually-passing testcase.
 */
class CucumberReportParserTest {

  @Test
  void countsScenariosNotStepsAndFindsTheFailedOne(@TempDir Path dir) throws IOException {
    String cucumberJson = """
        [
          {
            "uri": "features/login.feature",
            "elements": [
              {
                "keyword": "Background",
                "name": "",
                "steps": [
                  {"result": {"status": "passed"}}
                ]
              },
              {
                "keyword": "Scenario",
                "name": "Successful login",
                "steps": [
                  {"result": {"status": "passed"}},
                  {"result": {"status": "passed"}},
                  {"result": {"status": "passed"}}
                ]
              },
              {
                "keyword": "Scenario",
                "name": "Login with bad password",
                "steps": [
                  {"result": {"status": "passed"}},
                  {"result": {"status": "failed", "error_message": "expected error banner"}}
                ]
              }
            ]
          }
        ]
        """;
    Path reportFile = dir.resolve("cucumber.json");
    Files.writeString(reportFile, cucumberJson, StandardCharsets.UTF_8);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 1000L);

    assertEquals(2, report.total(), "Background must not be counted, and each scenario counts once regardless of step count");
    assertEquals(1, report.passed());
    assertEquals(1, report.failed());
    assertEquals(0, report.skipped());
    assertEquals("FAILED", report.status());
    assertEquals(1, report.failedScenarios().size());
    TestRunReport.FailedScenario failedScenario = report.failedScenarios().get(0);
    assertEquals("Login with bad password", failedScenario.scenario());
    assertEquals("features/login.feature", failedScenario.feature());
    assertEquals("expected error banner", failedScenario.error());
  }

  @Test
  void scenarioWithOnlySkippedStepsCountsAsSkippedNotFailed(@TempDir Path dir) throws IOException {
    String cucumberJson = """
        [
          {
            "uri": "features/checkout.feature",
            "elements": [
              {
                "type": "scenario",
                "keyword": "Scenario",
                "name": "Skipped by hook",
                "steps": [
                  {"result": {"status": "skipped"}},
                  {"result": {"status": "skipped"}}
                ]
              }
            ]
          }
        ]
        """;
    Path reportFile = write(dir, cucumberJson);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 500L);

    assertEquals(1, report.total());
    assertEquals(0, report.failed());
    assertEquals(1, report.skipped());
    assertEquals("PASSED", report.status());
    assertTrue(report.failedScenarios().isEmpty());
  }

  @Test
  void pendingUndefinedAndAmbiguousStepsFailTheScenario(@TempDir Path dir) throws IOException {
    Path reportFile = write(dir, """
        [{"uri": "features/checkout.feature", "elements": [
          {"type": "scenario", "keyword": "Scenario", "name": "pending one", "steps": [
            {"name": "a", "result": {"status": "passed"}},
            {"name": "b", "result": {"status": "pending", "error_message": "TODO: implement me"}}]},
          {"type": "scenario", "keyword": "Scenario", "name": "undefined one", "steps": [
            {"name": "the cart is empty", "result": {"status": "undefined"}}]},
          {"type": "scenario", "keyword": "Scenario", "name": "ambiguous one", "steps": [
            {"name": "c", "result": {"status": "ambiguous", "error_message": "Ambiguous step definitions"}}]}
        ]}]
        """);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 1L);

    assertEquals("FAILED", report.status());
    assertEquals(3, report.failed());
    assertEquals(0, report.passed());
    assertEquals("TODO: implement me", report.failedScenarios().get(0).error());
    assertTrue(report.failedScenarios().get(1).error().contains("the cart is empty"));
    assertEquals("Ambiguous step definitions", report.failedScenarios().get(2).error());
  }

  @Test
  void failedHooksFailTheScenarioEvenWhenStepsAreSkippedOrPassed(@TempDir Path dir) throws IOException {
    Path reportFile = write(dir, """
        [{"uri": "features/login.feature", "elements": [
          {"type": "scenario", "keyword": "Scenario", "name": "before hook broke",
           "before": [{"result": {"status": "failed", "error_message": "driver did not start"}}],
           "steps": [{"result": {"status": "skipped"}}]},
          {"type": "scenario", "keyword": "Scenario", "name": "after hook broke",
           "steps": [{"result": {"status": "passed"}}],
           "after": [{"result": {"status": "failed", "error_message": "cleanup failed"}}]},
          {"type": "scenario", "keyword": "Scenario", "name": "after step hook broke",
           "steps": [{"result": {"status": "passed"},
                      "after": [{"result": {"status": "failed", "error_message": "screenshot failed"}}]}]}
        ]}]
        """);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 1L);

    assertEquals("FAILED", report.status());
    assertEquals(3, report.failed());
    assertEquals("driver did not start", report.failedScenarios().get(0).error());
    assertEquals("cleanup failed", report.failedScenarios().get(1).error());
    assertEquals("screenshot failed", report.failedScenarios().get(2).error());
  }

  @Test
  void failedBackgroundStepFailsTheFollowingScenarioOnly(@TempDir Path dir) throws IOException {
    Path reportFile = write(dir, """
        [{"uri": "features/login.feature", "elements": [
          {"type": "background", "keyword": "Background", "name": "", "steps": [
            {"result": {"status": "failed", "error_message": "login page down"}}]},
          {"type": "scenario", "keyword": "Scenario", "name": "first", "steps": [
            {"result": {"status": "skipped"}}]},
          {"type": "background", "keyword": "Background", "name": "", "steps": [
            {"result": {"status": "passed"}}]},
          {"type": "scenario", "keyword": "Scenario", "name": "second", "steps": [
            {"result": {"status": "passed"}}]}
        ]}]
        """);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 1L);

    assertEquals(2, report.total());
    assertEquals(1, report.failed());
    assertEquals(1, report.passed());
    assertEquals("first", report.failedScenarios().get(0).scenario());
    assertEquals("login page down", report.failedScenarios().get(0).error());
  }

  @Test
  void countsScenariosByTypeRegardlessOfKeywordLanguage(@TempDir Path dir) throws IOException {
    Path reportFile = write(dir, """
        [{"uri": "features/login.feature", "elements": [
          {"type": "scenario", "keyword": "Example", "name": "english example", "steps": [
            {"result": {"status": "passed"}}]},
          {"type": "scenario", "keyword": "Skenario", "name": "indonesian scenario", "steps": [
            {"result": {"status": "failed", "error_message": "boom"}}]},
          {"type": "scenario", "keyword": "Scenario Template", "name": "outline row", "steps": [
            {"result": {"status": "passed"}}]}
        ]}]
        """);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 1L);

    assertEquals(3, report.total());
    assertEquals(2, report.passed());
    assertEquals(1, report.failed());
  }

  @Test
  void clipsLongStackTracesInFailedScenarioErrors(@TempDir Path dir) throws IOException {
    StringBuilder trace = new StringBuilder("java.lang.AssertionError: expected 200");
    for (int i = 0; i < 60; i++) trace.append("\\n\\tat com.example.Step.line").append(i).append("(Step.java)");
    Path reportFile = write(dir, """
        [{"uri": "f.feature", "elements": [
          {"type": "scenario", "keyword": "Scenario", "name": "s", "steps": [
            {"result": {"status": "failed", "error_message": "%s"}}]}
        ]}]
        """.formatted(trace));

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 1L);

    String error = report.failedScenarios().get(0).error();
    assertTrue(error.startsWith("java.lang.AssertionError: expected 200"));
    assertTrue(error.lines().count() <= 21, "stack trace must be clipped, got " + error.lines().count());
    assertTrue(error.contains("more lines"));
  }

  @Test
  void aggregatesSeveralJunitReports(@TempDir Path dir) throws IOException {
    Path first = dir.resolve("TEST-a.xml");
    Files.writeString(first, """
        <testsuite name="a"><testcase name="one" classname="A"/>
          <testcase name="two" classname="A"><failure message="bad"/></testcase></testsuite>
        """, StandardCharsets.UTF_8);
    Path second = dir.resolve("TEST-b.xml");
    Files.writeString(second, """
        <testsuite name="b"><testcase name="three" classname="B"><skipped/></testcase></testsuite>
        """, StandardCharsets.UTF_8);

    TestRunReport report = CucumberReportParser.parseJunitXml(List.of(first, second), "@ui", 1L);

    assertEquals(3, report.total());
    assertEquals(1, report.passed());
    assertEquals(1, report.failed());
    assertEquals(1, report.skipped());
    assertEquals("FAILED", report.status());
  }

  private Path write(Path dir, String json) throws IOException {
    Path reportFile = dir.resolve("cucumber.json");
    Files.writeString(reportFile, json, StandardCharsets.UTF_8);
    return reportFile;
  }

  @Test
  void attributesFailureOnlyToTheTestcaseItActuallyBelongsTo(@TempDir Path dir) throws IOException {
    String junitXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="suite" tests="3" failures="1" skipped="0" time="1.0">
          <testcase name="passing one" classname="LoginTest">
          </testcase>
          <testcase name="failing one" classname="LoginTest">
            <failure message="expected error banner">stack trace here</failure>
          </testcase>
          <testcase name="passing two" classname="LoginTest">
          </testcase>
        </testsuite>
        """;
    Path reportFile = dir.resolve("junit.xml");
    Files.writeString(reportFile, junitXml, StandardCharsets.UTF_8);

    TestRunReport report = CucumberReportParser.parseJunitXml(reportFile, "@ui", 750L);

    assertEquals(3, report.total());
    assertEquals(1, report.failed());
    assertEquals(2, report.passed());
    assertEquals(1, report.failedScenarios().size(),
        "only the testcase that actually contains a <failure> must be reported");
    assertEquals("failing one", report.failedScenarios().get(0).scenario());
    assertEquals("expected error banner", report.failedScenarios().get(0).error());
  }
  @Test
  void junitErrorsAreFailuresNotPasses(@TempDir Path dir) throws IOException {
    Path reportFile = dir.resolve("junit-error.xml");
    Files.writeString(reportFile, """
        <testsuite name="suite" tests="1" failures="0" errors="1" skipped="0">
          <testcase name="crashed scenario" classname="CheckoutTest">
            <error message="driver crashed">stack trace</error>
          </testcase>
        </testsuite>
        """, StandardCharsets.UTF_8);

    TestRunReport report = CucumberReportParser.parseJunitXml(reportFile, "@ui", 100L);

    assertEquals("FAILED", report.status());
    assertEquals(1, report.total());
    assertEquals(1, report.failed());
    assertEquals(0, report.passed());
    assertEquals("crashed scenario", report.failedScenarios().get(0).scenario());
  }

}
