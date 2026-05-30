package io.github.ygrip.testara.agent.cli;

import io.github.ygrip.testara.agent.cli.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

import java.io.InputStream;
import java.util.Properties;

@Command(
    name = "testara-agent",
    mixinStandardHelpOptions = true,
    versionProvider = TestaraAgentCli.VersionProvider.class,
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

  /** Reads version from META-INF/maven/.../pom.properties packed into the JAR. */
  static class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
      return new String[]{"testara-agent " + readVersion()};
    }

    static String readVersion() {
      try (InputStream is = VersionProvider.class.getResourceAsStream(
          "/META-INF/maven/io.github.ygrip/testara-agent-cli/pom.properties")) {
        if (is != null) {
          Properties props = new Properties();
          props.load(is);
          return props.getProperty("version", "unknown");
        }
      } catch (Exception ignored) {}
      return "unknown";
    }
  }
}
