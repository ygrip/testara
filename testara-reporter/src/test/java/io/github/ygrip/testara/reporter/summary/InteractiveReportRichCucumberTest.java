package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.render.JteReportRenderer;
import io.github.ygrip.testara.reporter.view.CucumberReportViewFactory;

class InteractiveReportRichCucumberTest {
  @TempDir Path tempDir;

  @Test
  void preservesScenarioOutlineTablesDocStringsAndMediaAttachments() throws Exception {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.getInteractive().setEnabled(true);
    String reportPath = System.getProperty("user.dir") + "/src/test/resources/cucumber/rich/cucumber-rich.json";
    var features = CucumberReportReader.readReports(reportPath);
    var view = new CucumberReportViewFactory().create(features, "Automation", configuration);

    var scenario = view.scenarios().getFirst();
    assertTrue(scenario.exampleExecution());
    assertEquals("Example #2", scenario.exampleLabel());
    assertEquals(2, scenario.steps().getFirst().dataTable().size());
    assertTrue(scenario.steps().get(1).docString().contains("campaign"));
    assertEquals(2, scenario.steps().get(2).attachments().size());

    Path output = tempDir.resolve("report.html");
    JteReportRenderer.INSTANCE.render(ReportStyle.SINGLE_PAGE, view, output);
    String html = Files.readString(output);

    assertTrue(html.contains("Example #2"));
    assertTrue(html.contains("step-data-table"));
    assertTrue(html.contains("step-doc-string"));
    assertTrue(html.contains("attachment-image"));
    assertTrue(html.contains("attachment-video"));
    assertTrue(html.contains("data:image/png;base64,aGVsbG8="));
    assertTrue(html.contains("data:video/mp4;base64,aGVsbG8="));
  }
}
