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
 * Goal which read all cucumber report json files, and try to merge them into single json file
 */
@Mojo(name = "aggregate-summary", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class AggregateSummaryReportMojo extends AbstractMojo {
  private final Logger log = Logger.getLogger(AggregateSummaryReportMojo.class.getName());
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  public MavenProject project;
  /**
   * Relative path of the cucumber reports, accept full filepath for single report and directory path if there are multiple reports exists
   *
   * @property property="${target-location}"
   * @required
   */
  @Parameter(property = "target-location", defaultValue = "/target/destination", required = true)
  private String targetLocation;

  /**
   * The desired value for merged cucumber json file name
   *
   * @property property="${report-name}"
   * @required
   */
  @Parameter(property = "report-name", defaultValue = "aggregate-summary.json", required = true)
  private String reportName;

  /**
   * Option to disable goal : scenario-outline
   *
   * @required
   */
  @Parameter(defaultValue = "false")
  private boolean skip;

  public void execute() throws MojoExecutionException {
    if (!skip) {
      String path = resolvePath();
      String report = resolveReportName();
      String projectName = resolveProjectName();
      log.info(String.format("Start processing custom reporter for %s", projectName));
      try {
        CucumberSummaryReportGenerator.fromLocation(path)
            .aggregate(report);
      } catch (Exception e) {
        e.printStackTrace();
        throw new MojoExecutionException("Fail to aggregate cucumber json report");
      }
    } else {
      log.info("Skip custom reporter");
    }
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

  private String resolveReportName() throws MojoExecutionException {
    String output = reportName;
    if (reportName == null || reportName.trim().isEmpty()) {
      String temp = System.getProperty("custom.report.report-name");
      if (temp != null && !temp.trim().isEmpty()) {
        output = temp;
      }
    }
    if (output == null || output.trim().isEmpty()) {
      throw new MojoExecutionException("No report file name is specified");
    }
    return output;
  }
}
