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
    assertTrue(html.contains("data-empty-results"));
    assertTrue(html.contains("class=\"step-number\""));
    assertTrue(html.contains(".clean-scenario-table tr[hidden]{display:none!important}"));
    assertTrue(html.contains("if (empty) empty.hidden = visible !== 0;"));
    assertTrue(html.contains("listView.hidden = true;\n    detailView.hidden = false;"));
    assertFalse(html.contains("detailView.scrollIntoView"));
    assertTrue(html.contains("rowToRestore.focus"));
    assertTrue(html.contains("data-report-page=\"scenarios\""));
    assertTrue(html.contains(".scenario-detail-view{width:100%;max-width:100%;min-width:0;overflow-x:clip"));
    assertTrue(html.contains(".scenario-detail-panel{width:100%;max-width:100%;min-width:0"));
    assertTrue(html.contains(".scenario-detail-head>div:first-child{min-width:0;max-width:100%"));
    assertTrue(html.contains(".scenario-detail-head h3,.scenario-detail-head p,.detail-tags,.step-title-row strong{overflow-wrap:anywhere;word-break:break-word"));
    assertTrue(html.contains(".rich-step-list,.rich-step-item,.step-content,.step-artifact,.attachment-grid,.attachment-card{min-width:0;max-width:100%"));
    assertTrue(html.contains(".scenario-detail-view .error-box{max-width:100%;overflow-wrap:anywhere;word-break:break-word"));
    assertTrue(html.contains(".step-data-table-shell{width:100%;max-width:100%;overflow-x:auto"));
    assertFalse(html.contains(".results-card{min-width:0;overflow:hidden"));
    assertFalse(html.contains("max-width:calc(100vw - 54px)"));
    assertFalse(html.contains("function animate("));
    assertFalse(html.contains("data-mobile-sort-strip"));
    assertFalse(html.contains("<button class=\"clear-icon-button\""));
    assertFalse(html.contains("data-failed-only"));
    assertFalse(html.contains("data-filter-pane-toggle"));
    assertFalse(html.contains("data-detail-toggle"));
  }
}
