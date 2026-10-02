package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestSummarySkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-summary",
  aliases = {"test-summary"},
  description = "Summarize feature files at scenario, feature, or directory level",
  mixinStandardHelpOptions = true
)
public class TestSummaryCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Path to a .feature file or directory (absolute, or relative to --project)")
  private Path target;

  @Option(names = "--scenario", description = "Filter to a specific scenario name (substring match)")
  private String scenarioFilter;

  @Option(names = "--concise", defaultValue = "false", description = "Token-efficient output for AI assistants")
  private boolean concise;

  @Option(names = "--project", defaultValue = ".", description = "Project root (default: current directory)")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = Map.of("concise", String.valueOf(concise));
    // The summary parses the target itself; no project index (and no .testara-agent/ cache) is needed.
    return CliSupport.print(new TestSummarySkill().execute(
      new TestSummarySkill.Input(target, scenarioFilter), CliSupport.contextWithoutIndex(root, AgentMode.READ_ONLY, opts)
    ));
  }
}
