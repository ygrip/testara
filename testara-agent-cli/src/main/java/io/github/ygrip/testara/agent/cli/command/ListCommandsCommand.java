package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.ListCommandsSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;

@Command(name = "/list-commands", aliases = {"list-commands"},
    description = "List all available Testara commands discovered in the project",
    mixinStandardHelpOptions = true)
public class ListCommandsCommand implements Runnable {

  @Parameters(index = "0", defaultValue = ".", description = "Project root to scan (default: current directory)")
  private Path projectRoot;

  @Option(names = "--format", defaultValue = "markdown", description = "Output format: markdown (default) or json")
  private String format;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.READ_ONLY,
        new DisabledLlmClient(), Map.of("format", format));
    System.out.println(new ListCommandsSkill().execute(null, ctx));
  }
}
