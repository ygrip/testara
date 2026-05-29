package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestOverviewSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;

@Command(
    name = "/test-overview",
    aliases = {"test-overview"},
    description = "Statistical overview of the entire test project",
    mixinStandardHelpOptions = true
)
public class TestOverviewCommand implements Runnable {

  @Parameters(index = "0", defaultValue = ".", description = "Project or feature root (default: current directory)")
  private Path target;

  @Option(names = "--format", defaultValue = "markdown",
      description = "Output format: markdown (default), json")
  private String format;

  @Override
  public void run() {
    Path projectRoot = target.toAbsolutePath().normalize();
    ProjectIndexer indexer = new ProjectIndexer();
    TestaraProjectProfile profile = indexer.index(projectRoot);

    AgentContext context = new AgentContext(
        projectRoot, profile, AgentMode.READ_ONLY,
        new DisabledLlmClient(), Map.of("format", format));

    TestOverviewSkill skill = new TestOverviewSkill();
    System.out.println(skill.execute(projectRoot, context));
  }
}
