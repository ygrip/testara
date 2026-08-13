package io.github.ygrip.testara.reporter.plugin;

import java.util.logging.Logger;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.ygrip.testara.reporter.cucumber.CucumberSummaryReportGenerator;

/**
 * Goal which read all cucumber report json files and generate single page report
 */
@Mojo(name = "cucumber-summary", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class GenerateCucumberSummaryReportMojo extends AbstractMojo {
  private final Logger log = Logger.getLogger(GenerateCucumberSummaryReportMojo.class.getName());
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  public MavenProject project;
  /**
   * Relative path of the cucumber reports, accept full filepath for single report and directory path if there are multiple reports exists
   *
   * @property property="${target-location}"
   * @required
   */
  @Parameter(property = "target-location", defaultValue = "/target/destination/", required = true)
  private String targetLocation;

  /**
   * Relative path of the cucumber summary reports will be generated
   *
   * @property property="${output-location}"
   * @required
   */
  @Parameter(property = "output-location", defaultValue = "/target/destination/", required = true)
  private String outputLocation;

  /**
   * Html report template to use in generating template
   *
   * @property property="${report-template}"
   * @required
   */
  @Parameter(property = "report-template", defaultValue = "modern")
  private String reportTemplate;

  /**
   * Report file name
   *
   * @property property="${report-name}"
   */
  @Parameter(property = "report-name", defaultValue = "summary")
  private String reportName;

  /**
   * Option to disable goal : scenario-outline
   */
  @Parameter(defaultValue = "false")
  private boolean skip;

  public void execute() throws MojoExecutionException {
    if (!skip) {
      String path = resolvePath();
      String targetPath = resolveTargetPath();
      String projectName = resolveProjectName();
      String template = resolveReportTemplate();
      String reportFileName = resolveReportFileName();
      log.info(String.format("Start generating cucumber custom summary report for %s",
          projectName));
      try {
        CucumberSummaryReportGenerator.fromLocation(path)
            .withOutputFileName(reportFileName)
            .withReportTemplate(template)
            .withReportName(projectName)
            .toLocation(targetPath)
            .generateReport();
      } catch (Exception e) {
        e.printStackTrace();
        throw new MojoExecutionException("Fail to generate cucumber custom summary report");
      }
    } else {
      log.info("Skip generating cucumber custom summary reporter");
    }
  }

  private String resolveReportTemplate() {
    String output = reportTemplate;
    if (reportTemplate == null || reportTemplate.trim().isEmpty()) {
      String temp = System.getProperty("custom.report.report-template");
      if (temp != null && !temp.trim().isEmpty()) {
        output = temp;
      }
    }
    if (output == null || output.trim().isEmpty()) {
      output = "modern";
    }
    return output;
  }

  private String resolveReportFileName() {
    String output = reportName;
    if (reportName == null || reportName.trim().isEmpty()) {
      String temp = System.getProperty("custom.report.report-name");
      if (temp != null && !temp.trim().isEmpty()) {
        output = temp;
      }
    }
    if (output == null || output.trim().isEmpty()) {
      output = "summary";
    }
    return output;
  }

  private String resolveTargetPath() throws MojoExecutionException {
    String output = outputLocation;
    if (outputLocation == null || outputLocation.trim().isEmpty()) {
      String temp = System.getProperty("custom.report.output-location");
      if (temp != null && !temp.trim().isEmpty()) {
        output = temp;
      }
    }
    if (output == null || output.trim().isEmpty()) {
      output = resolvePath();
    }
    return output;
  }

  private String resolveProjectName() {
    return project == null ? "automation" : project.getName();
  }

  private String resolvePath() throws MojoExecutionException {
    String output = targetLocation;
    if (targetLocation == null || targetLocation.trim().isEmpty()) {
      String temp = System.getProperty("custom.report.target-location");
      if (temp != null && !temp.trim().isEmpty()) {
        output = temp;
      }
    }
    if (output == null || output.trim().isEmpty()) {
      throw new MojoExecutionException("No report target location is specified");
    }
    return output;
  }
}
