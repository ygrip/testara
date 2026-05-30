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
import java.util.HashMap;
import java.util.Map;

@Command(name = "/test-validation", aliases = {"test-validation"},
    description = "List project validations, show validation detail, or generate a new ValidatorLogic class",
    mixinStandardHelpOptions = true)
public class TestValidationCommand implements Runnable {

  @Parameters(index = "0", arity = "0..1",
      description = "Description to generate a validation, 'detail:<name>' to show detail, or omit to list all")
  private String description;

  @Option(names = {"--list", "-l"}, description = "List all indexed validations in this project")
  private boolean list;

  @Option(names = {"--detail", "-d"}, description = "Show source, when-to-use and how-to-use for a specific validation")
  private String detail;

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
    Map<String, String> opts = new HashMap<>();
    opts.put("mode", mode);
    opts.put("package", pkg);
    if (detail != null) opts.put("detail", detail);

    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.PATCH,
        new DisabledLlmClient(), opts);

    String input = list ? "--list" : (description != null ? description : "");
    System.out.println(new TestValidationSkill().execute(input, ctx));
  }
}
