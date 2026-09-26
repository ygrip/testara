package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestRunSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-run",
  aliases = {"test-run"},
  description = "Resolve natural-language test intent to tag expression, optionally execute. "
    + "Exit code: 0 passed or plan only, 1 failed/timed out/preflight failed, 2 blocked or invalid input.",
  mixinStandardHelpOptions = true
)
public class TestRunCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Natural-language test run intent, e.g. 'run payment smoke tests'")
  private String intent;

  @Option(names = "--dry-run",
    arity = "0..1",
    description = "Show the plan only. Wins over --execute. Without --execute only the plan is shown."
  )
  private Boolean dryRun;

  @Option(names = "--execute", defaultValue = "false", description = "Actually execute the build")
  private boolean execute;

  @Option(names = "--rerun-failed", defaultValue = "false", description = "Re-run previously failed scenarios")
  private boolean rerunFailed;

  @Option(names = "--module", description = "Restrict to a module: relative path (modules/api) or :artifactId")
  private String module;

  @Option(names = "--timeout-minutes", description = "Kill the build after this many minutes (default 15)")
  private Integer timeoutMinutes;

  @Option(names = "--gradle-task", description = "Gradle projects only: test task to run (default test)")
  private String gradleTask;

  @Option(names = "--report",
    defaultValue = "markdown",
    description = "Output format of an executed run: markdown, json"
  )
  private String reportFormat;

  @Option(names = "--project", defaultValue = ".", description = "Project root directory")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = CliSupport.projectOptions(root);
    boolean run = execute && !Boolean.TRUE.equals(dryRun);
    opts.put("dryRun", String.valueOf(!run));
    opts.put("execute", String.valueOf(run));
    opts.put("rerunFailed", String.valueOf(rerunFailed));
    opts.put("format", reportFormat);
    if (module != null) {
      opts.put("module", module);
    }
    if (timeoutMinutes != null) {
      opts.put("timeoutMinutes", timeoutMinutes.toString());
    }
    if (gradleTask != null) {
      opts.put("gradleTask", gradleTask);
    }
    AgentMode mode = AgentMode.PLAN;
    if (run) {
      mode = AgentMode.APPLY;
    }
    String output = new TestRunSkill().execute(intent, CliSupport.context(root, mode, opts));
    System.out.println(output);
    return TestRunSkill.exitCode(output);
  }
}
