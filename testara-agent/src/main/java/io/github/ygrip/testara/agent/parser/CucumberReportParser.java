package io.github.ygrip.testara.agent.parser;

import io.github.ygrip.testara.agent.skill.run.TestRunReport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Cucumber execution reports (cucumber.json and JUnit XML) into
 * structured models for the agent to generate concise run reports.
 */
public final class CucumberReportParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String UNKNOWN_ERROR = "Unknown error";
  private static final int MAX_ERROR_LINES = 20;

  private CucumberReportParser() { /* utility */ }

  /** Keep the first {@value #MAX_ERROR_LINES} lines of an error so stack traces stay readable. */
  static String clip(String message) {
    List<String> lines = message.lines().toList();
    if (lines.size() <= MAX_ERROR_LINES) {
      return message;
    }
    return String.join("\n", lines.subList(0, MAX_ERROR_LINES))
        + "\n... (" + (lines.size() - MAX_ERROR_LINES) + " more lines)";
  }

  /**
   * Parse a cucumber.json file and return a structured TestRunReport.
   *
   * <p>Scenarios are selected by {@code type == "scenario"} (keywords are localized and include
   * {@code Example}/{@code Scenario Template}). cucumber.json carries no per-scenario status, so the
   * verdict folds every result that belongs to the scenario: the preceding {@code background}
   * element's steps, the scenario's {@code before}/{@code after} hooks, its steps and their
   * step-level hooks. Any failed/pending/undefined/ambiguous result fails the scenario (Cucumber's
   * strict default); otherwise any skipped result marks it skipped; otherwise it passed.
   */
  public static TestRunReport parseCucumberJson(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    JsonNode root = MAPPER.readTree(reportFile.toFile());

    int passed = 0;
    int failed = 0;
    int skipped = 0;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();

    for (JsonNode feature : root) {
      String featureUri = feature.path("uri").asText("unknown");
      JsonNode background = null;
      for (JsonNode element : feature.path("elements")) {
        if (isBackground(element)) {
          background = element;
          continue;
        }
        ScenarioOutcome outcome = new ScenarioOutcome();
        if (background != null) {
          outcome.addResults(background.path("steps"));
        }
        outcome.addScenario(element);
        background = null;

        switch (outcome.status()) {
          case FAILED -> {
            failed++;
            failedScenarios.add(new TestRunReport.FailedScenario(
                featureUri, element.path("name").asText("unknown"), outcome.errorMessage()));
          }
          case SKIPPED -> skipped++;
          case PASSED -> passed++;
        }
      }
    }

    String status = failed > 0 ? "FAILED" : "PASSED";
    int total = passed + failed + skipped;

    return new TestRunReport(status, durationMs, tagExpression,
        total, passed, failed, skipped,
        List.copyOf(failedScenarios));
  }

  /** Reports without {@code type} (very old formatters) fall back to the English keyword. */
  private static boolean isBackground(JsonNode element) {
    String type = element.path("type").asText("");
    if (!type.isEmpty()) {
      return "background".equals(type);
    }
    return "Background".equals(element.path("keyword").asText(""));
  }

  private enum ScenarioStatus { PASSED, FAILED, SKIPPED }

  /** Accumulates hook and step results of one scenario into a single verdict. */
  private static final class ScenarioOutcome {
    private boolean anyFailed;
    private boolean anySkipped;
    private String errorMessage;

    void addScenario(JsonNode scenario) {
      addResults(scenario.path("before"));
      addResults(scenario.path("steps"));
      addResults(scenario.path("after"));
    }

    /** Adds each step/hook result; steps may carry their own before/after (step) hooks. */
    void addResults(JsonNode items) {
      for (JsonNode item : items) {
        addResults(item.path("before"));
        add(item);
        addResults(item.path("after"));
      }
    }

    private void add(JsonNode item) {
      JsonNode result = item.path("result");
      String status = result.path("status").asText("");
      switch (status) {
        case "failed", "pending", "ambiguous" -> fail(result.path("error_message").asText(""),
            status + " step: " + item.path("name").asText("hook"));
        case "undefined" -> fail("", "Undefined step: " + item.path("name").asText("unknown"));
        case "skipped" -> anySkipped = true;
        default -> { /* passed, unused or unrecognized: no effect on the verdict */ }
      }
    }

    private void fail(String reportedMessage, String fallback) {
      anyFailed = true;
      if (errorMessage != null) {
        return;
      }
      if (reportedMessage.isBlank()) {
        errorMessage = fallback;
      } else {
        errorMessage = clip(reportedMessage);
      }
    }

    ScenarioStatus status() {
      if (anyFailed) {
        return ScenarioStatus.FAILED;
      }
      if (anySkipped) {
        return ScenarioStatus.SKIPPED;
      }
      return ScenarioStatus.PASSED;
    }

    String errorMessage() {
      if (errorMessage == null) {
        return UNKNOWN_ERROR;
      }
      return errorMessage;
    }
  }

  /**
   * Parse a JUnit XML report using a real DOM parser so failures are scoped to the
   * {@code <testcase>} they actually belong to, instead of a proximity heuristic over the raw XML.
   */
  public static TestRunReport parseJunitXml(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    return parseJunitXml(List.of(reportFile), tagExpression, durationMs);
  }

  /**
   * Parse and aggregate several JUnit XML reports (Surefire/Failsafe/Gradle write one file per test
   * class). Used as the run verdict fallback when no fresh cucumber.json was written.
   */
  public static TestRunReport parseJunitXml(List<Path> reportFiles, String tagExpression,
      long durationMs) throws IOException {
    int total = 0;
    int failed = 0;
    int skipped = 0;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();

    for (Path reportFile : reportFiles) {
      NodeList testcases = junitDocument(reportFile).getElementsByTagName("testcase");
      for (int i = 0; i < testcases.getLength(); i++) {
        Element testcase = (Element) testcases.item(i);
        total++;
        Element problem = junitProblem(testcase);
        if (problem != null) {
          failed++;
          failedScenarios.add(new TestRunReport.FailedScenario(
              testcase.getAttribute("classname"), testcase.getAttribute("name"), junitMessage(problem)));
        } else if (testcase.getElementsByTagName("skipped").getLength() > 0) {
          skipped++;
        }
      }
    }

    int passed = Math.max(0, total - failed - skipped);
    String status = failed > 0 ? "FAILED" : "PASSED";
    return new TestRunReport(status, durationMs, tagExpression,
        total, passed, failed, skipped,
        List.copyOf(failedScenarios));
  }

  private static Document junitDocument(Path reportFile) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(reportFile.toFile());
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Failed to parse JUnit XML report: " + reportFile, e);
    }
  }

  private static Element junitProblem(Element testcase) {
    NodeList failures = testcase.getElementsByTagName("failure");
    if (failures.getLength() > 0) {
      return (Element) failures.item(0);
    }
    NodeList errors = testcase.getElementsByTagName("error");
    if (errors.getLength() > 0) {
      return (Element) errors.item(0);
    }
    return null;
  }

  private static String junitMessage(Element problem) {
    String message = problem.getAttribute("message");
    if (message.isBlank()) {
      message = problem.getTextContent().strip();
    }
    if (message.isBlank()) {
      return UNKNOWN_ERROR;
    }
    return clip(message);
  }
}
