package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

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
  void rendersCleanTableExplorerWithInPlaceScenarioDetail() throws Exception {
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
    assertTrue(html.contains("data-quality-signal"));
    assertTrue(html.contains("data-quality-breakdown"));
    assertTrue(html.contains("data-coverage-scroll"));
    assertTrue(html.contains("data-clean-explorer-table"));
    assertTrue(html.contains("data-responsive-table-scroll"));
    assertTrue(html.contains("<svg class=\"search-icon\""));
    assertTrue(html.contains("data-inline-clear"));
    assertTrue(html.contains("data-sort-column=\"status\""));
    assertTrue(html.contains("data-sort-column=\"name\""));
    assertTrue(html.contains("data-sort-column=\"feature\""));
    assertTrue(html.contains("data-sort-column=\"duration\""));
    assertTrue(html.contains("data-scenario-list-view"));
    assertTrue(html.contains("data-scenario-detail-view"));
    assertTrue(html.contains("data-open-scenario"));
    assertTrue(html.contains("data-close-scenario"));
    assertTrue(html.contains("listView.hidden = true;\n    detailView.hidden = false;"));
    assertTrue(html.contains("detailView.scrollIntoView"));
    assertTrue(html.contains("activeRow.focus"));
    assertFalse(html.contains("function animate("));
    assertFalse(html.contains("data-mobile-sort-strip"));
    assertFalse(html.contains("<button class=\"clear-icon-button\""));
    assertFalse(html.contains("data-failed-only"));
    assertFalse(html.contains("data-filter-pane-toggle"));
    assertFalse(html.contains("data-detail-toggle"));
  }
}
