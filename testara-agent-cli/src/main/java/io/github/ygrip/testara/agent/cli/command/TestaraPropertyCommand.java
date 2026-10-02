package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestaraPropertySkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "/testara-property",
  aliases = {"testara-property"},
  description = "Manage property keys: list, suggest a key for a value, generate a config block, or explain properties() rules",
  mixinStandardHelpOptions = true
)
public class TestaraPropertyCommand implements Callable<Integer> {

  @Option(names = "--mode", defaultValue = "list", description = "list (default), suggest, generate, rules")
  private String mode;

  @Option(names = "--domain", description = "Domain name for generated keys")
  private String domain;

  @Option(names = "--value", description = "Value to suggest a property key for (mode suggest)")
  private String value;

  @Option(names = "--slice", description = "Slice for mode generate: api, ui, sql, mongo, kafka")
  private String slice;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    return CliSupport.print(new TestaraPropertySkill().execute(
      new TestaraPropertySkill.Input(mode, domain, value, slice),
      CliSupport.contextWithoutIndex(root, AgentMode.READ_ONLY, Map.of())
    ));
  }
}
