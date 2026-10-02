package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestaraContextSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "/testara-context",
  aliases = {"testara-context"},
  description = "Show the project's Testara runtime context: slices, config coverage, steps, commands, validations",
  mixinStandardHelpOptions = true
)
public class TestaraContextCommand implements Callable<Integer> {

  @Parameters(index = "0", defaultValue = ".", description = "Project root (default: current directory)")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    return CliSupport.print(new TestaraContextSkill().execute(
      null, CliSupport.context(root, AgentMode.READ_ONLY, Map.of())
    ));
  }
}
