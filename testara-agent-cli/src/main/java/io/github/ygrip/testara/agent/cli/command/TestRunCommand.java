package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestRunSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Command(name = "/test-run", aliases = {"test-run"},
    description = "Resolve natural-language test intent to tag expression, optionally execute",
    mixinStandardHelpOptions = true)
public class TestRunCommand implements Runnable {

  @Parameters(index = "0", description = "Natural-language test run intent, e.g. 'run payment smoke tests'")
  private String intent;

  @Option(names = "--dry-run",  defaultValue = "true",  description = "Show plan only (default: true)")
  private boolean dryRun;

  @Option(names = "--execute",  defaultValue = "false", description = "Actually execute Maven")
  private boolean execute;

  @Option(names = "--rerun-failed", defaultValue = "false", description = "Re-run previously failed scenarios")
  private boolean rerunFailed;

  @Option(names = "--module",   description = "Restrict to a specific Maven module")
  private String module;

  @Option(names = "--report",   defaultValue = "markdown", description = "Output format: markdown, json")
  private String reportFormat;

  @Option(names = "--project",  defaultValue = ".", description = "Project root directory")
  private Path projectRoot;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    Map<String, String> opts = new HashMap<>();
    opts.put("dryRun",   String.valueOf(dryRun && !execute));
    opts.put("execute",  String.valueOf(execute));
    opts.put("rerunFailed", String.valueOf(rerunFailed));
    opts.put("format",   reportFormat);
    if (module != null) opts.put("module", module);

    AgentContext ctx = new AgentContext(root,
        new ProjectIndexer().index(root), AgentMode.PLAN,
        new DisabledLlmClient(), opts);
    System.out.println(new TestRunSkill().execute(intent, ctx));
  }
}
