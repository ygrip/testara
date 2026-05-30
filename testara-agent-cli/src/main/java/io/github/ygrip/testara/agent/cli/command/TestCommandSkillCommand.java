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
import java.util.HashMap;
import java.util.Map;

@Command(name = "/test-command", aliases = {"test-command"},
    description = "List project commands, show command detail, or generate a new CommandLogic<T> class",
    mixinStandardHelpOptions = true)
public class TestCommandSkillCommand implements Runnable {

  @Parameters(index = "0", arity = "0..1",
      description = "Description to generate a command, 'detail:<name>' to show detail, or omit to list all")
  private String description;

  @Option(names = {"--list", "-l"}, description = "List all indexed commands in this project")
  private boolean list;

  @Option(names = {"--detail", "-d"}, description = "Show source and usage docs for a specific command")
  private String detail;

  @Option(names = "--package", defaultValue = "com.company.automation.commands",
      description = "Target package for generated class")
  private String pkg;

  @Option(names = "--return-type", defaultValue = "String",
      description = "Return type of the generated command")
  private String returnType;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    Map<String, String> opts = new HashMap<>();
    opts.put("package", pkg);
    opts.put("returnType", returnType);
    if (detail != null) opts.put("detail", detail);

    AgentContext ctx = new AgentContext(root,
        JsonlKnowledgeStore.loadProfile(root), AgentMode.PATCH,
        new DisabledLlmClient(), opts);

    String input = list ? "--list" : (description != null ? description : "");
    System.out.println(new TestCommandSkill().execute(input, ctx));
  }
}
