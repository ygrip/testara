package io.github.ygrip.testara.agent.parser;

import io.github.ygrip.testara.agent.skill.run.TestRunReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Cucumber execution reports (cucumber.json and JUnit XML) into
 * structured models for the agent to generate concise run reports.
 */
public final class CucumberReportParser {

  private static final Logger LOG = Logger.getLogger(CucumberReportParser.class.getName());

  // cucumber.json patterns
  private static final Pattern JSON_STATUS   = Pattern.compile("\"status\"\\s*:\\s*\"(passed|failed|pending|skipped)\"");
  private static final Pattern JSON_NAME     = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern JSON_URI      = Pattern.compile("\"uri\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern JSON_LINE     = Pattern.compile("\"line\"\\s*:\\s*(\\d+)");
  private static final Pattern JSON_KEYWORD  = Pattern.compile("\"keyword\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern JSON_MESSAGE  = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern JSON_ELEMENT  = Pattern.compile("\"elements\"\\s*:\\s*\\[");

  // JUnit XML patterns
  private static final Pattern JUNIT_TESTCASE = Pattern.compile(
      "<testcase\\s+(?:[^>]*\\s)?name=\"([^\"]+)\"(?:[^>]*\\s)?classname=\"([^\"]+)\"[^>]*>");
  private static final Pattern JUNIT_FAILURE = Pattern.compile(
      "<failure[^>]*message=\"([^\"]+)\"[^>]*>");
  private static final Pattern JUNIT_TESTSUITE = Pattern.compile(
      "<testsuite[^>]*tests=\"(\\d+)\"[^>]*failures=\"(\\d+)\"[^>]*skipped=\"(\\d+)\"[^>]*time=\"([^\"]+)\"[^>]*>");

  private CucumberReportParser() { /* utility */ }

  /**
   * Parse a cucumber.json file and return a structured TestRunReport.
   */
  public static TestRunReport parseCucumberJson(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    String json = Files.readString(reportFile, StandardCharsets.UTF_8);

    int passed = 0, failed = 0, skipped = 0;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();

    // Count statuses
    Matcher statusMatcher = JSON_STATUS.matcher(json);
    while (statusMatcher.find()) {
      switch (statusMatcher.group(1)) {
        case "passed"  -> passed++;
        case "failed"  -> failed++;
        case "skipped", "pending" -> skipped++;
      }
    }

    // Extract failed scenario details by parsing scenario blocks
    // Find scenarios with failed status and extract their name + feature URI
    Pattern scenarioBlock = Pattern.compile(
        "\"keyword\"\\s*:\\s*\"(?:Scenario|Scenario Outline)\"[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"status\"\\s*:\\s*\"failed\"",
        Pattern.DOTALL);

    Matcher scenarioMatcher = scenarioBlock.matcher(json);
    while (scenarioMatcher.find()) {
      String name = scenarioMatcher.group(1);
      // Find the feature URI for context
      String featureUri = findFeatureUri(json, scenarioMatcher.start());

      // Find the error message
      String error = findErrorMessage(json, scenarioMatcher.start());

      failedScenarios.add(new TestRunReport.FailedScenario(
          featureUri != null ? featureUri : "unknown",
          name,
          error != null ? error : "Unknown error"));
    }

    String status = failed > 0 ? "FAILED" : "PASSED";
    int total = passed + failed + skipped;

    return new TestRunReport(status, durationMs, tagExpression,
        total, passed, failed, skipped,
        List.copyOf(failedScenarios));
  }

  /**
   * Parse a JUnit XML report.
   */
  public static TestRunReport parseJunitXml(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    String xml = Files.readString(reportFile, StandardCharsets.UTF_8);

    int total = 0, failed = 0, skipped = 0;

    Matcher suiteMatch = JUNIT_TESTSUITE.matcher(xml);
    while (suiteMatch.find()) {
      total   += Integer.parseInt(suiteMatch.group(1));
      failed  += Integer.parseInt(suiteMatch.group(2));
      skipped += Integer.parseInt(suiteMatch.group(3));
    }

    int passed = total - failed - skipped;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();

    // Extract failed test case names
    Matcher tcMatch = JUNIT_TESTCASE.matcher(xml);
    while (tcMatch.find()) {
      String name = tcMatch.group(1);
      String className = tcMatch.group(2);
      // Check if this test case has a failure element nearby
      Matcher failMatch = JUNIT_FAILURE.matcher(xml.substring(tcMatch.start()));
      if (failMatch.find() && failMatch.start() < 2000) { // within reasonable proximity
        failedScenarios.add(new TestRunReport.FailedScenario(
            className, name, failMatch.group(1)));
      }
    }

    String status = failed > 0 ? "FAILED" : "PASSED";
    return new TestRunReport(status, durationMs, tagExpression,
        total, passed, failed, skipped,
        List.copyOf(failedScenarios));
  }

  /**
   * Auto-detect report format and parse accordingly.
   */
  public static TestRunReport parse(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    String content = Files.readString(reportFile, StandardCharsets.UTF_8);
    if (content.strip().startsWith("{")) {
      return parseCucumberJson(reportFile, tagExpression, durationMs);
    } else if (content.strip().startsWith("<")) {
      return parseJunitXml(reportFile, tagExpression, durationMs);
    }
    throw new IOException("Unknown report format in " + reportFile);
  }

  // ── Helpers ──────────────────────────────────────────────────────

  private static String findFeatureUri(String json, int fromIndex) {
    // Search backwards for the nearest "uri" before this scenario
    String before = json.substring(0, Math.max(0, fromIndex));
    Matcher m = JSON_URI.matcher(before);
    String last = null;
    while (m.find()) last = m.group(1);
    return last;
  }

  private static String findErrorMessage(String json, int fromIndex) {
    // Search forward for the nearest "message" after this position
    String after = json.substring(fromIndex, Math.min(json.length(), fromIndex + 5000));
    Matcher m = JSON_MESSAGE.matcher(after);
    return m.find() ? m.group(1) : null;
  }
}
