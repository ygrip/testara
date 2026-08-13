package io.github.ygrip.testara.reporter.cucumber;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.common.base.Stopwatch;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;
import io.github.ygrip.testara.reporter.model.AggregateSummary;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.render.JteReportRenderer;
import io.github.ygrip.testara.reporter.render.ReportRenderer;
import io.github.ygrip.testara.reporter.view.CucumberReportViewFactory;
import io.github.ygrip.testara.reporter.view.ReportView;

public class CucumberSummaryReportGenerator {
  private static final Logger LOG = Logger.getLogger(CucumberSummaryReportGenerator.class.getName());
  private static final CucumberReportViewFactory VIEW_FACTORY = new CucumberReportViewFactory();
  private static final ReportRenderer RENDERER = JteReportRenderer.INSTANCE;
  private static final String INTERACTIVE_REPORT_NAME = "report";

  private final String DIR = System.getProperty("user.dir");
  private final String REPORT_PATH;
  private final String FULLPATH;
  private String OUTPUT_PATH;
  private String OUTPUT_NAME = "summary";
  private String reportName = "Application In Test";
  private ReportStyle reportStyle;

  public CucumberSummaryReportGenerator(String reportPath) {
    REPORT_PATH = reportPath;
    FULLPATH = DIR + REPORT_PATH;
    OUTPUT_PATH = FULLPATH;
  }

  public static CucumberSummaryReportGenerator fromLocation(String reportPath) {
    return new CucumberSummaryReportGenerator(reportPath);
  }

  public CucumberSummaryReportGenerator withReportName(String reportName) {
    if (reportName != null && !reportName.isBlank()) {
      this.reportName = reportName;
    }
    return this;
  }

  public CucumberSummaryReportGenerator withReportStyle(ReportStyle style) {
    if (style != null) {
      this.reportStyle = style;
    }
    return this;
  }

  /**
   * @deprecated Use {@link #withReportStyle(ReportStyle)}. Known legacy template identifiers are
   * mapped to JTE report styles; arbitrary template loading is no longer supported.
   */
  @Deprecated
  public CucumberSummaryReportGenerator withReportTemplate(String template) {
    if (template != null && !template.isBlank()) {
      this.reportStyle = ReportStyle.from(template);
    }
    return this;
  }

  public CucumberSummaryReportGenerator withOutputFileName(String outputFileName) {
    if (outputFileName != null && !outputFileName.isBlank()) {
      this.OUTPUT_NAME = outputFileName;
    }
    return this;
  }

  public CucumberSummaryReportGenerator toLocation(String outputPath) {
    if (outputPath != null && !outputPath.isBlank()) {
      this.OUTPUT_PATH = DIR + outputPath;
    }
    return this;
  }

  public String mergeReportAs(String fileName, boolean needCleanUp) throws Exception {
    if (needCleanUp) {
      CucumberJsonFormatter.fromTargetLocation(REPORT_PATH).overwrite(true).rewriteScenarioWithOutlines();
    }
    return CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(FULLPATH))
      .mergeAs(FULLPATH + File.separator + fileName);
  }

  public String mergeReportAs(String fileName) throws Exception {
    return mergeReportAs(fileName, true);
  }

  public AggregateSummary aggregate(String fileName, boolean needCleanUp) throws Exception {
    if (needCleanUp) {
      CucumberJsonFormatter.fromTargetLocation(REPORT_PATH).overwrite(true).rewriteScenarioWithOutlines();
    }
    return CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(FULLPATH))
      .aggregate(FULLPATH + File.separator + fileName);
  }

  public AggregateSummary aggregate(String fileName) throws Exception {
    return aggregate(fileName, true);
  }

  public void generateReport(boolean needCleanUp) throws Exception {
    if (needCleanUp) {
      CucumberJsonFormatter.fromTargetLocation(REPORT_PATH).overwrite(true).rewriteScenarioWithOutlines();
    }
    writeReport(reportName, FULLPATH, OUTPUT_NAME);
  }

  public void generateReport() throws Exception {
    generateReport(true);
  }

  public void writeReport(String reportName, String reportPath, String fileName) throws Exception {
    Stopwatch stopwatch = Stopwatch.createStarted();
    this.reportName = reportName;
    ReportConfiguration configuration = reportConfiguration();
    ReportStyle style = reportStyle == null ? ReportStyle.from(configuration.getStyle()) : reportStyle;
    List<Feature> features = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(reportPath))
      .getMergedFeatures();
    ReportView view = VIEW_FACTORY.create(features, reportName, configuration);
    Path output = Path.of(OUTPUT_PATH, fileName + ".html");
    RENDERER.render(style, view, output);

    Path interactiveOutput = renderInteractiveCompanion(configuration, style, view, output);
    String locations = interactiveOutput == null
      ? output.toAbsolutePath().toString()
      : output.toAbsolutePath() + System.lineSeparator() + "Interactive report : " + interactiveOutput.toAbsolutePath();

    LOG.info(String.format(
      "Generating %s report %s took %s ms%nReport location : %s",
      style.name().toLowerCase(), reportName,
      stopwatch.stop().elapsed(TimeUnit.MILLISECONDS), locations
    ));
  }

  private Path renderInteractiveCompanion(
      ReportConfiguration configuration,
      ReportStyle style,
      ReportView view,
      Path summaryOutput
  ) throws Exception {
    if (!configuration.getInteractive().isEnabled() || style == ReportStyle.SINGLE_PAGE) {
      return null;
    }

    Path interactiveOutput = Path.of(OUTPUT_PATH, INTERACTIVE_REPORT_NAME + ".html");
    if (interactiveOutput.toAbsolutePath().normalize().equals(summaryOutput.toAbsolutePath().normalize())) {
      interactiveOutput = Path.of(OUTPUT_PATH, "interactive-report.html");
    }
    RENDERER.render(ReportStyle.SINGLE_PAGE, view, interactiveOutput);
    return interactiveOutput;
  }

  private ReportConfiguration reportConfiguration() {
    try {
      ReportConfiguration configuration = TestFramework.configuration().get(ReportConfiguration.class);
      return configuration == null ? new ReportConfiguration() : configuration;
    } catch (RuntimeException exception) {
      LOG.fine("Reporter configuration is not initialized; using Testara defaults: " + exception.getMessage());
      return new ReportConfiguration();
    }
  }
}
