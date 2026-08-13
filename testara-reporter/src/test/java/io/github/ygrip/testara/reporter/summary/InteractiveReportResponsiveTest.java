package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.cucumber.CucumberReportMergeFactory;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.render.JteReportRenderer;
import io.github.ygrip.testara.reporter.view.CucumberReportViewFactory;

class InteractiveReportResponsiveTest {
  @TempDir Path tempDir;

  @Test
  void rendersSetaraRameinBlendWithMobileFilterAndSortControls() throws Exception {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.getInteractive().setEnabled(true);
    String reportPath = System.getProperty("user.dir") + "/src/test/resources/cucumber/multiple/";
    var features = CucumberReportMergeFactory.Builder
      .using(CucumberReportReader.getReportPaths(reportPath))
      .getMergedFeatures();
    var view = new CucumberReportViewFactory().create(features, "Automation", configuration);
    Path output = tempDir.resolve("report.html");

    JteReportRenderer.INSTANCE.render(ReportStyle.SINGLE_PAGE, view, output);

    String html = Files.readString(output);
    assertTrue(html.contains("data-visual-blend=\"setara-ramein\""));
    assertTrue(html.contains("--violet:#8B5CF6"));
    assertTrue(html.contains("data-filter-pane-toggle"));
    assertTrue(html.contains("aria-controls=\"scenario-filter-pane\""));
    assertTrue(html.contains("id=\"scenario-filter-pane\""));
    assertTrue(html.contains("data-mobile-sort-strip"));
  }
}
