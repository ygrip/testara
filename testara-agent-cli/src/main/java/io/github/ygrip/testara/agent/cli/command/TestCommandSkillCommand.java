package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestCommandSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Command(name = "/test-command", aliases = {"test-command"},
    description = "Generate a Testara CommandLogic<T> class from a description",
    mixinStandardHelpOptions = true)
public class TestCommandSkillCommand implements Runnable {

  @Parameters(index = "0", description = "Description of the command, e.g. 'generate customer id with prefix and timestamp'")
  private String description;

  @Option(names = "--package",     defaultValue = "com.company.automation.commands",
      description = "Target package for the generated class")
  private String pkg;

  @Option(names = "--return-type", defaultValue = "String",
      description = "Return type of the command")
  private String returnType;

  @Option(names = "--project",     defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    Map<String, String> opts = Map.of("package", pkg, "returnType", returnType);
    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.PATCH,
        new DisabledLlmClient(), opts);
    System.out.println(new TestCommandSkill().execute(description, ctx));
  }
}
