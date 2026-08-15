package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    int totalSteps = view.scenarios().stream().mapToInt(item -> item.steps().size()).sum();

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
    assertTrue(html.indexOf("href=\"#scenarios\"") < html.indexOf("href=\"#additional-info\""));
    assertTrue(html.contains("class=\"report-nav-icon\""));
    assertTrue(html.contains("data-report-page=\"overview\""));
    assertTrue(html.contains("data-report-page=\"additional-info\""));
    assertTrue(html.contains("data-report-page=\"failures\""));
    assertTrue(html.contains("data-report-page=\"hotspots\""));
    assertTrue(html.contains("data-report-page=\"coverage\""));
    assertTrue(html.contains("data-report-page=\"scenarios\""));
    assertTrue(html.contains("hashchange"));

    assertTrue(html.contains("data-theme-toggle"));
    assertTrue(html.contains("testara-report-theme"));
    assertTrue(html.contains("prefers-color-scheme: dark"));
    assertTrue(html.contains("data-theme-icon-light"));
    assertTrue(html.contains("data-theme-icon-dark"));
    assertTrue(html.contains("data-ui-refinement=\"navigation-accessibility-contrast\""));
    assertTrue(html.contains("--nav-control-size:44px"));
    assertTrue(html.contains("--chart-voice-text:#173F45"));
    assertTrue(html.contains("--chart-voice-text:#D9ECEF"));
    assertTrue(html.contains(".report-theme-toggle{min-height:var(--nav-control-size)"));
    assertTrue(html.contains("border-radius:999px"));
    assertTrue(html.contains(".report-nav-link{min-height:var(--nav-control-size)"));
    assertTrue(html.contains("class=\"report-navigation-shell\""));
    assertTrue(html.contains("class=\"report-theme-container\""));
    assertTrue(html.indexOf("</nav>") < html.indexOf("class=\"report-theme-container\""));
    assertTrue(html.contains(".report-nav-link span{display:inline!important}"));
    assertTrue(html.contains(".theme-toggle-label{display:none!important}"));

    assertTrue(html.contains("data-additional-info-scroll"));
    assertTrue(html.contains("role=\"tablist\""));
    assertTrue(html.contains("data-hotspot-tab=\"scenarios\""));
    assertTrue(html.contains("data-hotspot-tab=\"steps\""));
    assertTrue(html.contains("data-hotspot-panel=\"scenarios\""));
    assertTrue(html.contains("data-hotspot-panel=\"steps\""));

    assertTrue(html.contains("data-scenario-hooks=\"before\""));
    assertTrue(html.contains("data-scenario-hooks=\"after\""));
    assertTrue(html.contains("Before scenario"));
    assertTrue(html.contains("After scenario"));
    assertFalse(html.contains("<strong>Scenario hooks</strong>"));
    assertTrue(html.contains("data-step-hooks-dialog"));
    assertEquals(0, occurrences(html, "class=\"step-hook-info-button\""));
    assertEquals(totalSteps, occurrences(html, "class=\"step-hooks-panel\""));
    assertEquals(totalSteps, occurrences(html, "class=\"step-item rich-step-item\" data-open-step-hooks"));
    assertTrue(html.contains("data-step-line"));
    assertTrue(html.contains("trigger.dataset.stepLine"));
    assertTrue(html.contains("document.querySelectorAll('[data-open-step-hooks]').forEach(function (row)"));
    assertFalse(html.contains("<span>Step hooks</span>"));
    assertFalse(html.contains("data-inline-step-hook"));
    assertFalse(html.contains("· offset "));

    assertTrue(html.contains("attachment-visual-stage"));
    assertTrue(html.contains("object-fit:contain"));

    assertTrue(html.contains("error-chevron"));
    assertFalse(html.contains("class=\"error-toggle-label\""));
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

  private static int occurrences(String value, String needle) {
    int count = 0;
    int index = 0;
    while ((index = value.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
  }
}
