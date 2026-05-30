package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestPlanSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "/test-plan", aliases = {"test-plan"},
    description = "Generate a Testara-compatible Cucumber feature from user intent",
    mixinStandardHelpOptions = true)
public class TestPlanCommand implements Runnable {

  @Parameters(index = "0", description = "Intent, e.g. 'Create tests for refund approval flow'")
  private String intent;

  @Option(names = "--slice", defaultValue = "api",
      description = "Layer slice: api, ui, database, streaming, fullstack")
  private String slice;

  @Option(names = "--domain", description = "Domain name override (auto-inferred if not set)")
  private String domain;

  @Option(names = "--tag", description = "Extra tags to add", arity = "0..*")
  private List<String> tags;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.PATCH,
        new DisabledLlmClient(), Map.of());
    System.out.println(new TestPlanSkill().execute(
        new TestPlanSkill.Input(intent, slice, domain, tags != null ? tags : List.of()), ctx));
  }
}
