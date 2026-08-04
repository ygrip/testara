package io.github.ygrip.testara.agent.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
                "keyword": "Scenario",
                "name": "Not yet implemented",
                "steps": [
                  {"result": {"status": "passed"}},
                  {"result": {"status": "pending"}}
                ]
              }
            ]
          }
        ]
        """;
    Path reportFile = dir.resolve("cucumber.json");
    Files.writeString(reportFile, cucumberJson, StandardCharsets.UTF_8);

    TestRunReport report = CucumberReportParser.parseCucumberJson(reportFile, "@ui", 500L);

    assertEquals(1, report.total());
    assertEquals(0, report.failed());
    assertEquals(1, report.skipped());
    assertEquals("PASSED", report.status());
    assertTrue(report.failedScenarios().isEmpty());
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
}
