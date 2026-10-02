package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestaraDbSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "/testara-db",
  aliases = {"testara-db"},
  description = "Explain and generate SQL, Mongo, Elasticsearch or Kafka config and feature templates",
  mixinStandardHelpOptions = true
)
public class TestaraDbCommand implements Callable<Integer> {

  @Option(names = "--slice", defaultValue = "sql", description = "sql (default), mongo, kafka, elastic")
  private String slice;

  @Option(names = "--mode", defaultValue = "explain", description = "explain (default), config, feature")
  private String mode;

  @Option(names = "--name", description = "Service name, e.g. settlementDb, paymentKafka")
  private String name;

  @Override
  public Integer call() {
    return CliSupport.print(new TestaraDbSkill().execute(
      new TestaraDbSkill.Input(slice, mode, name),
      CliSupport.contextWithoutIndex(CliSupport.root(Path.of(".")), AgentMode.READ_ONLY, Map.of())
    ));
  }
}
