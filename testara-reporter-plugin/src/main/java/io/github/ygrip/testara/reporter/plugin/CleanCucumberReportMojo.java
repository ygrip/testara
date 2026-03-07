package io.github.ygrip.testara.reporter.plugin;

import java.util.logging.Logger;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;

/**
 * Goal which read all cucumber report json files, and try to resolve scenario outline id with it's generated name
 */
@Mojo(name = "clean-cucumber", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class CleanCucumberReportMojo extends AbstractMojo {
  private final Logger log = Logger.getLogger(CleanCucumberReportMojo.class.getName());
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
   * Option to overwrite the cucumber report
   *
   * @property property="${overwrite}"
   * @required
   */
  @Parameter(property = "overwrite", defaultValue = "true")
  private boolean overwrite;

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
      String projectName = resolveProjectName();
      log.info(String.format("Start processing custom reporter for %s", projectName));
      try {
        CucumberJsonFormatter.fromTargetLocation(path)
            .overwrite(this.overwrite)
            .rewriteScenarioWithOutlines();
      } catch (Exception e) {
        e.printStackTrace();
        throw new MojoExecutionException("Fail to clean cucumber json report");
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
}
