package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestReviewSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;

@Command(name = "/test-review", aliases = {"test-review"},
    description = "Review feature files for quality issues",
    mixinStandardHelpOptions = true)
public class TestReviewCommand implements Runnable {

  @Parameters(index = "0", description = "Path to a .feature file or directory")
  private Path target;

  @Override
  public void run() {
    Path root = target.toAbsolutePath().normalize();
    AgentContext ctx = new AgentContext(root,
        new ProjectIndexer().index(root), AgentMode.READ_ONLY,
        new DisabledLlmClient(), Map.of());
    System.out.println(new TestReviewSkill().execute(target, ctx));
  }
}
