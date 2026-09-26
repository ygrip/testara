package io.github.ygrip.testara.agent.skill.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRunReportTest {

  @Test
  void jsonRoundTripsMultilineFailureDetails() throws Exception {
    TestRunReport report = new TestRunReport(
        "FAILED", 1234L, "@api and not @slow",
        1, 0, 1, 0,
        List.of(new TestRunReport.FailedScenario(
            "features/test.feature",
            "fails with \"quoted\" name",
            "line one\nline two\\path")));

    var json = new ObjectMapper().readTree(report.toJson());

    assertEquals("FAILED", json.path("status").asText());
    assertEquals("line one\nline two\\path",
        json.path("failedScenarios").path(0).path("error").asText());
  }
}
