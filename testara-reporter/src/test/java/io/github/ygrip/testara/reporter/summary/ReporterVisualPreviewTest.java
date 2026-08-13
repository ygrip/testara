package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.cucumber.CucumberReportMergeFactory;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.render.JteReportRenderer;
import io.github.ygrip.testara.reporter.view.CucumberReportViewFactory;
import io.github.ygrip.testara.reporter.view.ReportView;

class ReporterVisualPreviewTest {

  @Test
  void generatesBlibliReporterPreviews() throws Exception {
    String fixturePath = System.getProperty("user.dir") + "/src/test/resources/cucumber/multiple/";
    var features = CucumberReportMergeFactory.Builder
      .using(CucumberReportReader.getReportPaths(fixturePath))
      .getMergedFeatures();

    ReportConfiguration summaryConfiguration = configuration(false);
    ReportConfiguration interactiveConfiguration = configuration(true);
    CucumberReportViewFactory factory = new CucumberReportViewFactory();

    ReportView summary = factory.create(features, "Affiliate Platform Regression", summaryConfiguration);
    ReportView interactive = factory.create(features, "Affiliate Platform Regression", interactiveConfiguration);

    Path preview = Path.of("target", "reporter-preview");
    Files.createDirectories(preview);
    Path summaryFile = preview.resolve("summary.html");
    Path reportFile = preview.resolve("report.html");

    JteReportRenderer.INSTANCE.render(ReportStyle.MODERN, summary, summaryFile);
    JteReportRenderer.INSTANCE.render(ReportStyle.SINGLE_PAGE, interactive, reportFile);

    assertTrue(Files.size(summaryFile) > 0);
    assertTrue(Files.size(reportFile) > 0);
  }

  private ReportConfiguration configuration(boolean interactive) {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.setOrganizationName("Blibli Affiliate");
    configuration.setOrganizationLogo("https://www.static-src.com/siva/asset/07_2026/White-Blibli-Logo-2026-07-23.png");
    configuration.setOrganizationDetail("Blibli Affiliate - Automation reporting");
    configuration.setCustomFields(new LinkedHashMap<>(Map.of(
      "environment", "QA2",
      "browser", "Chrome 150",
      "build", "affiliate-2026.08.13.4",
      "team", "Affiliate Platform"
    )));
    configuration.getInteractive().setEnabled(interactive);
    return configuration;
  }
}
