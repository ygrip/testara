package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestSummarySkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;

@Command(
    name = "/test-summary",
    aliases = {"test-summary"},
    description = "Summarize feature files at scenario, feature, or directory level",
    mixinStandardHelpOptions = true
)
public class TestSummaryCommand implements Runnable {

  @Parameters(index = "0", description = "Path to a .feature file or directory")
  private Path target;

  @Option(names = "--scenario", description = "Filter to a specific scenario name (substring match)")
  private String scenarioFilter;

  @Option(names = "--concise", defaultValue = "false",
      description = "Token-efficient output for AI assistants")
  private boolean concise;

  @Override
  public void run() {
    Path projectRoot = target.toAbsolutePath().normalize();
    TestaraProjectProfile profile = JsonlKnowledgeStore.loadProfile(projectRoot);

    AgentContext context = new AgentContext(
        projectRoot, profile, AgentMode.READ_ONLY, new DisabledLlmClient(),
        Map.of("concise", String.valueOf(concise)));

    TestSummarySkill skill = new TestSummarySkill();
    String result = skill.execute(new TestSummarySkill.Input(target, scenarioFilter), context);
    System.out.println(result);
  }
}
