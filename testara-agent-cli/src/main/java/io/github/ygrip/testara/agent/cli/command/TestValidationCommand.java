package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.skill.AgentContext;
import io.github.ygrip.testara.agent.skill.TestValidationSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;

@Command(name = "/test-validation", aliases = {"test-validation"},
    description = "Generate a validation JSON or ValidatorLogic class from a description",
    mixinStandardHelpOptions = true)
public class TestValidationCommand implements Runnable {

  @Parameters(index = "0", description = "Description of the validation, e.g. 'response contains active users sorted by date'")
  private String description;

  @Option(names = "--mode", defaultValue = "auto",
      description = "Generation mode: auto (default), json, java")
  private String mode;

  @Option(names = "--package", defaultValue = "com.company.automation.validators")
  private String pkg;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    Map<String, String> opts = Map.of("mode", mode, "package", pkg);
    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.PATCH,
        new DisabledLlmClient(), opts);
    System.out.println(new TestValidationSkill().execute(description, ctx));
  }
}
