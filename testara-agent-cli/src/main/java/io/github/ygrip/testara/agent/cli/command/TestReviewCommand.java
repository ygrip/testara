package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestReviewSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-review",
  aliases = {"test-review"},
  description = "Review feature files for quality issues",
  mixinStandardHelpOptions = true
)
public class TestReviewCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Path to a .feature file or directory (absolute, or relative to --project)")
  private Path target;

  @Option(names = "--project", defaultValue = ".", description = "Project root (default: current directory)")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    return CliSupport.print(new TestReviewSkill().execute(
      target, CliSupport.context(root, AgentMode.READ_ONLY, Map.of())
    ));
  }
}
