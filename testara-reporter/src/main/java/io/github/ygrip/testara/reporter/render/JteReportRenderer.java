package io.github.ygrip.testara.reporter.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.FileOutput;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.view.ReportView;

public final class JteReportRenderer implements ReportRenderer {
  public static final JteReportRenderer INSTANCE = new JteReportRenderer();
  private static final TemplateEngine TEMPLATE_ENGINE = TemplateEngine.createPrecompiled(ContentType.Html);

  private JteReportRenderer() {
  }

  @Override
  public void render(ReportStyle style, ReportView report, Path output) throws IOException {
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (FileOutput fileOutput = new FileOutput(output)) {
      TEMPLATE_ENGINE.render(style.template(), report, fileOutput);
    } catch (RuntimeException exception) {
      throw new IOException(
        "Failed to render Testara " + style.name().toLowerCase() + " report to " + output,
        exception
      );
    }
  }
}
