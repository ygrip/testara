package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.model.ReportStyle;
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

    ReportView view = new CucumberReportViewFactory().create(List.of(), "Affiliate Automation", configuration);
    Path output = tempDir.resolve("report.html");
    JteReportRenderer.INSTANCE.render(ReportStyle.MODERN, view, output);

    String html = Files.readString(output);
    assertTrue(html.contains("Blibli Affiliate"));
    assertTrue(html.contains("https://example.com/blibli.png"));
    assertTrue(html.contains("Blibli Affiliate - Automation reporting"));
    assertTrue(html.contains("<!--[if mso]>"));
    assertFalse(html.contains("data-th-"));
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
}
