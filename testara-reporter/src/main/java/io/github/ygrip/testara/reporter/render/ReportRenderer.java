package io.github.ygrip.testara.reporter.render;

import java.io.IOException;
import java.nio.file.Path;

import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.view.ReportView;

public interface ReportRenderer {
  void render(ReportStyle style, ReportView report, Path output) throws IOException;
}
