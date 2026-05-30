package io.github.ygrip.testara.agent.cli;

import io.github.ygrip.testara.agent.cli.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "testara-agent",
    mixinStandardHelpOptions = true,
    version = "testara-agent 2.0.0",
    description = "Testara Agent — agentic skills for Testara automation projects",
    subcommands = {
        TestSummaryCommand.class,
        TestOverviewCommand.class,
        TestReviewCommand.class,
        TestRunCommand.class,
        TestCommandSkillCommand.class,
        TestValidationCommand.class,
        TestPlanCommand.class,
        TestInitCommand.class,
        ListCommandsCommand.class,
        ListValidationsCommand.class,
        ListUiCatalogCommand.class,
        KnowledgeCommand.class,
        McpCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class TestaraAgentCli implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    int exit = new CommandLine(new TestaraAgentCli()).execute(args);
    System.exit(exit);
  }
}
