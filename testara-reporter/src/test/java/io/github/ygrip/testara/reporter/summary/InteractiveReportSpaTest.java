package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class InteractiveReportSpaTest {
  @TempDir Path tempDir;

  @Test
  void rendersScopedSpaPagesAndFocusedScenarioInteractions() throws Exception {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.getInteractive().setEnabled(true);
    configuration.getCustomFields().put("environment", "QA2");
    configuration.getCustomFields().put("build", "affiliate-2026.08.15");

    String reportPath = System.getProperty("user.dir") + "/src/test/resources/cucumber/rich/cucumber-rich.json";
    var features = CucumberReportReader.readReports(reportPath);
    var view = new CucumberReportViewFactory().create(features, "Automation", configuration);

    Path output = tempDir.resolve("report.html");
    JteReportRenderer.INSTANCE.render(ReportStyle.SINGLE_PAGE, view, output);
    String html = Files.readString(output);

    assertTrue(html.contains("data-report-navigation"));
    assertTrue(html.contains("href=\"#overview\""));
    assertTrue(html.contains("href=\"#additional-info\""));
    assertTrue(html.contains("href=\"#failures\""));
    assertTrue(html.contains("href=\"#hotspots\""));
    assertTrue(html.contains("href=\"#coverage\""));
    assertTrue(html.contains("href=\"#scenarios\""));
    assertTrue(html.contains("data-report-page=\"overview\""));
    assertTrue(html.contains("data-report-page=\"additional-info\""));
    assertTrue(html.contains("data-report-page=\"failures\""));
    assertTrue(html.contains("data-report-page=\"hotspots\""));
    assertTrue(html.contains("data-report-page=\"coverage\""));
    assertTrue(html.contains("data-report-page=\"scenarios\""));
    assertTrue(html.contains("hashchange"));

    assertTrue(html.contains("data-additional-info-scroll"));
    assertTrue(html.contains("role=\"tablist\""));
    assertTrue(html.contains("data-hotspot-tab=\"scenarios\""));
    assertTrue(html.contains("data-hotspot-tab=\"steps\""));
    assertTrue(html.contains("data-hotspot-panel=\"scenarios\""));
    assertTrue(html.contains("data-hotspot-panel=\"steps\""));

    assertTrue(html.contains("data-scenario-hooks"));
    assertTrue(html.contains("Scenario hooks"));
    assertTrue(html.contains("data-step-hooks-dialog"));
    assertTrue(html.contains("data-open-step-hooks"));
    assertFalse(html.contains("data-inline-step-hook"));
    assertFalse(html.contains("· offset "));

    assertTrue(html.contains("error-chevron"));
    assertFalse(html.contains("error-toggle-label"));
    assertFalse(html.contains(">Expand<"));
    assertFalse(html.contains(">Collapse<"));

    assertTrue(html.contains("data-open-image"));
    assertTrue(html.contains("data-open-video"));
    assertTrue(html.contains("data-media-viewer"));
    assertTrue(html.contains("data-image-zoom-in"));
    assertTrue(html.contains("data-image-zoom-out"));
    assertTrue(html.contains("data-image-reset"));
    assertTrue(html.contains("data-image-fit"));
    assertTrue(html.contains("data-image-prev"));
    assertTrue(html.contains("data-image-next"));
    assertTrue(html.contains("data-video-fullscreen"));
  }
}
