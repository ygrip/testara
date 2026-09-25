package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestCommandSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-command",
  aliases = {"test-command"},
  description = "List project commands, show command detail, or generate a new CommandLogic<T> class",
  mixinStandardHelpOptions = true
)
public class TestCommandSkillCommand implements Callable<Integer> {

  @Parameters(index = "0",
    arity = "0..1",
    description = "Description to generate a command, 'detail:<name>' to show detail, or omit to list all"
  )
  private String description;

  @Option(names = {"--list", "-l"}, description = "List all indexed commands in this project")
  private boolean list;

  @Option(names = {"--detail", "-d"}, description = "Show source and usage docs for a specific command")
  private String detail;

  @Option(names = "--package",
    description = "Target package for generated class (default: the project's command package)"
  )
  private String pkg;

  @Option(names = "--return-type", defaultValue = "String", description = "Return type of the generated command")
  private String returnType;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = new HashMap<>();
    if (pkg != null)
      opts.put("package", pkg);
    opts.put("returnType", returnType);
    if (detail != null)
      opts.put("detail", detail);

    String input = "";
    if (list) {
      input = "--list";
    } else if (description != null) {
      input = description;
    }
    return CliSupport.print(new TestCommandSkill().execute(input, CliSupport.context(root, AgentMode.PATCH, opts)));
  }
}
