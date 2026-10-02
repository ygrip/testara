package io.github.ygrip.testara.agent.skill.run;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds safe, template-based Maven commands for Cucumber test execution.
 * Rejects any input that could lead to shell injection.
 */
public class MavenCommandBuilder {

  /** Build `mvn verify -Dcucumber.filter.tags="expr"` for Failsafe-based Testara runners. */
  public String build(String tagExpression) {
    return build(tagExpression, null, true);
  }

  /** Build with optional module and verify flag. */
  public String build(String tagExpression, String module, boolean useVerify) {
    validate(tagExpression, module);
    String goal = useVerify ? "verify" : "test";
    String pl = (module != null && !module.isBlank()) ? "-pl " + module + " " : "";
    return String.format("mvn %s%s -Dcucumber.filter.tags=\"%s\"", pl, goal, tagExpression);
  }

  /**
   * Build the argv (one token per element, no shell involved) for a tag-filtered run. Use this
   * for actual process execution instead of {@link #build(String, String, boolean)}'s display
   * string, which is only safe to show, not to hand to a shell.
   */
  public List<String> buildArgv(String tagExpression, String module, boolean useVerify) {
    validate(tagExpression, module);
    List<String> argv = new ArrayList<>();
    argv.add(useVerify ? "verify" : "test");
    addModule(argv, module);
    argv.add("-Dcucumber.filter.tags=" + tagExpression);
    return List.copyOf(argv);
  }

  /**
   * Build the argv for a rerun-file run, using Cucumber's native {@code @<rerun-file>} feature-path
   * syntax (recognized by {@code TestaraCucumberEngineOptions.featuresWithLines()}) instead of
   * stuffing raw rerun-file content into {@code -Dcucumber.filter.tags}, which rejects the
   * {@code :}/{@code /}/{@code .} characters every rerun-file line contains.
   */
  public List<String> buildRerunArgv(Path rerunFile, String module, boolean useVerify) {
    RunArguments.requireValidModule(module);
    List<String> argv = new ArrayList<>();
    argv.add(useVerify ? "verify" : "test");
    addModule(argv, module);
    argv.add("-Dcucumber.features=@" + rerunFile);
    return List.copyOf(argv);
  }

  /** Executable tag-filtered run for {@code projectRoot}: wrapper or {@code mvn}, OS-aware. */
  public BuildCommand command(Path projectRoot, String tagExpression, String module) {
    List<String> args = buildArgv(tagExpression, module, true);
    return new BuildCommand(withLauncher(projectRoot, args), build(tagExpression, module, true));
  }

  /** Executable rerun-file run for {@code projectRoot}: wrapper or {@code mvn}, OS-aware. */
  public BuildCommand rerunCommand(Path projectRoot, Path rerunFile, String module) {
    List<String> args = buildRerunArgv(rerunFile, module, true);
    return new BuildCommand(withLauncher(projectRoot, args), "mvn " + String.join(" ", args));
  }

  private List<String> withLauncher(Path projectRoot, List<String> args) {
    List<String> argv = new ArrayList<>();
    argv.add(ProcessRunner.mavenLauncher(projectRoot));
    argv.addAll(args);
    return argv;
  }

  private void addModule(List<String> argv, String module) {
    if (module != null && !module.isBlank()) {
      argv.add("-pl");
      argv.add(module.strip());
    }
  }

  private void validate(String tagExpression, String module) {
    RunArguments.requireValidTagExpression(tagExpression);
    RunArguments.requireValidModule(module);
  }
}
