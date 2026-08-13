package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.cucumber.CucumberReportMergeFactory;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.render.JteReportRenderer;
import io.github.ygrip.testara.reporter.view.CucumberReportViewFactory;
import io.github.ygrip.testara.reporter.view.ReportView;

class JteReportRendererTest {
  @TempDir
  Path tempDir;

  @Test
  void rendersModernWhiteLabelReportAndPreservesOutlookComments() throws Exception {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.setOrganizationName("Blibli Affiliate");
    configuration.setOrganizationLogo("https://example.com/blibli.png");
    configuration.setOrganizationDetail("Blibli Affiliate - Automation reporting");
    configuration.setCustomFields(new LinkedHashMap<>(java.util.Map.of(
      "environment", "QA2",
      "browser", "Chrome 150"
    )));

    ReportView view = new CucumberReportViewFactory().create(List.of(), "Affiliate Automation", configuration);
    Path output = tempDir.resolve("summary.html");
    JteReportRenderer.INSTANCE.render(ReportStyle.MODERN, view, output);

    String html = Files.readString(output);
    assertTrue(html.contains("Blibli Affiliate"));
    assertTrue(html.contains("https://example.com/blibli.png"));
    assertTrue(html.contains("Blibli Affiliate - Automation reporting"));
    assertTrue(html.contains("QA2"));
    assertTrue(html.contains("Chrome 150"));
    assertTrue(html.contains("Pass rate"));
    assertTrue(html.contains("Test results"));
    assertTrue(html.contains("<!--[if mso]>"));
    assertTrue(html.contains("font-size:14px"));
    assertFalse(html.contains("<script"));
    assertFalse(html.contains("data-th-"));
  }

  @Test
  void doesNotMaterializeScenarioDetailsForSummaryByDefault() throws Exception {
    var features = reportFeatures();

    ReportView view = new CucumberReportViewFactory().create(features, "Automation", new ReportConfiguration());

    assertTrue(view.scenarios().isEmpty());
  }

  @Test
  void rendersInteractiveSinglePageWithScenarioSearchFilterSortAndDetails() throws Exception {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.getInteractive().setEnabled(true);
    ReportView view = new CucumberReportViewFactory().create(reportFeatures(), "Automation", configuration);

    assertFalse(view.scenarios().isEmpty());
    assertTrue(view.scenarios().stream().allMatch(scenario -> scenario.durationNanos() >= 0));

    Path output = tempDir.resolve("report.html");
    JteReportRenderer.INSTANCE.render(ReportStyle.SINGLE_PAGE, view, output);

    String html = Files.readString(output);
    assertTrue(html.contains("data-report-search"));
    assertTrue(html.contains("data-status-filter"));
    assertTrue(html.contains("data-feature-filter"));
    assertTrue(html.contains("data-tag-filter"));
    assertTrue(html.contains("data-report-sort"));
    assertTrue(html.contains("data-failed-only"));
    assertTrue(html.contains("data-scenario-row"));
    assertTrue(html.contains("<script"));
    assertTrue(html.contains("Scenario details"));
    assertTrue(html.contains("Failure signals"));
  }

  @Test
  void rendersEverySupportedStyle() throws Exception {
    ReportView view = new CucumberReportViewFactory().create(List.of(), "Automation", new ReportConfiguration());
    for (ReportStyle style : ReportStyle.values()) {
      Path output = tempDir.resolve(style.name().toLowerCase() + ".html");
      JteReportRenderer.INSTANCE.render(style, view, output);
      String html = Files.readString(output);
      assertTrue(html.contains("Automation"));
      assertTrue(html.contains("Testara"));
    }
  }

  private List<io.github.ygrip.testara.reporter.cucumber.Feature> reportFeatures() throws Exception {
    String reportPath = System.getProperty("user.dir") + "/src/test/resources/cucumber/multiple/";
    return CucumberReportMergeFactory.Builder
      .using(CucumberReportReader.getReportPaths(reportPath))
      .getMergedFeatures();
  }
}
