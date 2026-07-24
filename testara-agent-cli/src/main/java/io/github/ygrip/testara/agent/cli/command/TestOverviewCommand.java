package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestOverviewSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-overview",
  aliases = {"test-overview"},
  description = "Statistical overview of the entire test project",
  mixinStandardHelpOptions = true
)
public class TestOverviewCommand implements Runnable {

  @Parameters(index = "0", defaultValue = ".", description = "Project or feature root (default: current directory)")
  private Path target;

  @Option(names = "--format",
    defaultValue = "markdown",
    description = "Output format: markdown (default), json, concise"
  )
  private String format;

  @Option(names = "--concise", defaultValue = "false", description = "Token-efficient output for AI assistants")
  private boolean concise;

  @Override
  public void run() {
    Path projectRoot = target.toAbsolutePath()
      .normalize();
    TestaraProjectProfile profile = JsonlKnowledgeStore.loadProfile(projectRoot);

    Map<String, String> opts = new HashMap<>();
    opts.put("format", format);
    opts.put("concise", String.valueOf(concise));

    AgentContext context = new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, new DisabledLlmClient(), opts);

    TestOverviewSkill skill = new TestOverviewSkill();
    System.out.println(skill.execute(projectRoot, context));
  }
}
