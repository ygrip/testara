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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Cucumber execution reports (cucumber.json and JUnit XML) into
 * structured models for the agent to generate concise run reports.
 */
public final class CucumberReportParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CucumberReportParser() { /* utility */ }

  /**
   * Parse a cucumber.json file and return a structured TestRunReport.
   * A scenario's own outcome is derived from its steps' statuses (standard cucumber.json only
   * carries a status per step, never per scenario): any failed step fails the scenario, otherwise
   * any skipped/pending/undefined step marks it skipped, otherwise it passed.
   */
  public static TestRunReport parseCucumberJson(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    JsonNode root = MAPPER.readTree(reportFile.toFile());

    int passed = 0, failed = 0, skipped = 0;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();

    for (JsonNode feature : root) {
      String featureUri = feature.path("uri").asText("unknown");
      for (JsonNode element : feature.path("elements")) {
        String keyword = element.path("keyword").asText("");
        if (!"Scenario".equals(keyword) && !"Scenario Outline".equals(keyword)) {
          continue; // skip Background and other non-scenario elements
        }

        ScenarioOutcome outcome = scenarioOutcome(element);
        switch (outcome.status) {
          case FAILED -> failed++;
          case SKIPPED -> skipped++;
          case PASSED -> passed++;
        }

        if (outcome.status == ScenarioStatus.FAILED) {
          String name = element.path("name").asText("unknown");
          failedScenarios.add(new TestRunReport.FailedScenario(
              featureUri, name, outcome.errorMessage != null ? outcome.errorMessage : "Unknown error"));
        }
      }
    }

    String status = failed > 0 ? "FAILED" : "PASSED";
    int total = passed + failed + skipped;

    return new TestRunReport(status, durationMs, tagExpression,
        total, passed, failed, skipped,
        List.copyOf(failedScenarios));
  }

  private enum ScenarioStatus { PASSED, FAILED, SKIPPED }

  private record ScenarioOutcome(ScenarioStatus status, String errorMessage) { }

  private static ScenarioOutcome scenarioOutcome(JsonNode scenarioElement) {
    boolean anyFailed = false;
    boolean anySkipped = false;
    String errorMessage = null;
    for (JsonNode step : scenarioElement.path("steps")) {
      JsonNode result = step.path("result");
      String stepStatus = result.path("status").asText("");
      switch (stepStatus) {
        case "failed" -> {
          anyFailed = true;
          if (errorMessage == null) {
            errorMessage = result.path("error_message").asText(null);
          }
        }
        case "pending", "skipped", "undefined" -> anySkipped = true;
        default -> { /* passed or unrecognized: no-op */ }
      }
    }
    if (anyFailed) {
      return new ScenarioOutcome(ScenarioStatus.FAILED, errorMessage);
    }
    return new ScenarioOutcome(anySkipped ? ScenarioStatus.SKIPPED : ScenarioStatus.PASSED, null);
  }

  /**
   * Parse a JUnit XML report using a real DOM parser so failures are scoped to the
   * {@code <testcase>} they actually belong to, instead of a proximity heuristic over the raw XML.
   */
  public static TestRunReport parseJunitXml(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    Document doc;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      doc = builder.parse(reportFile.toFile());
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Failed to parse JUnit XML report: " + reportFile, e);
    }

    int total = 0, failed = 0, skipped = 0;
    NodeList testsuites = doc.getElementsByTagName("testsuite");
    for (int i = 0; i < testsuites.getLength(); i++) {
      Element suite = (Element) testsuites.item(i);
      total += intAttr(suite, "tests");
      failed += intAttr(suite, "failures");
      skipped += intAttr(suite, "skipped");
    }

    int passed = total - failed - skipped;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();

    NodeList testcases = doc.getElementsByTagName("testcase");
    for (int i = 0; i < testcases.getLength(); i++) {
      Element testcase = (Element) testcases.item(i);
      NodeList failures = testcase.getElementsByTagName("failure");
      if (failures.getLength() == 0) {
        continue;
      }
      Element failure = (Element) failures.item(0);
      String message = failure.hasAttribute("message") ? failure.getAttribute("message") : "Unknown error";
      failedScenarios.add(new TestRunReport.FailedScenario(
          testcase.getAttribute("classname"), testcase.getAttribute("name"), message));
    }

    String status = failed > 0 ? "FAILED" : "PASSED";
    return new TestRunReport(status, durationMs, tagExpression,
        total, passed, failed, skipped,
        List.copyOf(failedScenarios));
  }

  private static int intAttr(Element element, String name) {
    if (!element.hasAttribute(name)) {
      return 0;
    }
    try {
      return Integer.parseInt(element.getAttribute(name));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Auto-detect report format and parse accordingly.
   */
  public static TestRunReport parse(Path reportFile, String tagExpression,
      long durationMs) throws IOException {
    String content = Files.readString(reportFile, StandardCharsets.UTF_8).strip();
    if (content.startsWith("{") || content.startsWith("[")) {
      return parseCucumberJson(reportFile, tagExpression, durationMs);
    } else if (content.startsWith("<")) {
      return parseJunitXml(reportFile, tagExpression, durationMs);
    }
    throw new IOException("Unknown report format in " + reportFile);
  }
}
