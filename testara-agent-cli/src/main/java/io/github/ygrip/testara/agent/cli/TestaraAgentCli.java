package io.github.ygrip.testara.agent.cli;

import java.io.InputStream;
import java.util.Properties;

import io.github.ygrip.testara.agent.cli.command.KnowledgeCommand;
import io.github.ygrip.testara.agent.cli.command.ListCommandsCommand;
import io.github.ygrip.testara.agent.cli.command.ListUiCatalogCommand;
import io.github.ygrip.testara.agent.cli.command.ListValidationsCommand;
import io.github.ygrip.testara.agent.cli.command.McpCommand;
import io.github.ygrip.testara.agent.cli.command.TestCommandSkillCommand;
import io.github.ygrip.testara.agent.cli.command.TestInitCommand;
import io.github.ygrip.testara.agent.cli.command.TestOverviewCommand;
import io.github.ygrip.testara.agent.cli.command.TestPlanCommand;
import io.github.ygrip.testara.agent.cli.command.TestReviewCommand;
import io.github.ygrip.testara.agent.cli.command.TestRunCommand;
import io.github.ygrip.testara.agent.cli.command.TestSummaryCommand;
import io.github.ygrip.testara.agent.cli.command.TestValidationCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

@Command(name = "testara-agent",
  mixinStandardHelpOptions = true,
  versionProvider = TestaraAgentCli.VersionProvider.class,
  description = "Testara Agent — agentic skills for Testara automation projects",
  subcommands = {TestSummaryCommand.class, TestOverviewCommand.class, TestReviewCommand.class, TestRunCommand.class,
    TestCommandSkillCommand.class, TestValidationCommand.class, TestPlanCommand.class, TestInitCommand.class,
    ListCommandsCommand.class, ListValidationsCommand.class, ListUiCatalogCommand.class, KnowledgeCommand.class,
    McpCommand.class, CommandLine.HelpCommand.class}
)
public class TestaraAgentCli implements Runnable {

  public static void main(String[] args) {
    int exit = new CommandLine(new TestaraAgentCli()).execute(args);
    System.exit(exit);
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }


  /** Reads the build-filtered version resource retained by both shaded JARs and native images. */
  static class VersionProvider implements IVersionProvider {
    static String readVersion() {
      try (InputStream is = VersionProvider.class.getResourceAsStream(
        "/testara-agent-version.properties")) {
        if (is != null) {
          Properties props = new Properties();
          props.load(is);
          return props.getProperty("version", "unknown");
        }
      } catch (Exception ignored) {
      }
      return "unknown";
    }

    @Override
    public String[] getVersion() {
      return new String[] {"testara-agent " + readVersion()};
    }
  }
}
