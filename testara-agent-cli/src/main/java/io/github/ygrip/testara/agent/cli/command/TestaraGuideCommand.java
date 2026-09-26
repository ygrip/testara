package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestaraGuideSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "/testara-guide",
  aliases = {"testara-guide"},
  description = "Print the Testara generation rules and guardrails",
  mixinStandardHelpOptions = true
)
public class TestaraGuideCommand implements Callable<Integer> {

  @Parameters(index = "0",
    arity = "0..1",
    description = "Section: properties, request-spec, ui, quirks, db, kafka, steps, all (default)"
  )
  private String section;

  @Override
  public Integer call() {
    return CliSupport.print(new TestaraGuideSkill().execute(
      section, CliSupport.contextWithoutIndex(CliSupport.root(Path.of(".")), AgentMode.READ_ONLY, Map.of())
    ));
  }
}
