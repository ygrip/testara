package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestValidationSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-validation",
  aliases = {"test-validation"},
  description = "List project validations, show validation detail, or generate a new ValidatorLogic class",
  mixinStandardHelpOptions = true
)
public class TestValidationCommand implements Callable<Integer> {

  @Parameters(index = "0",
    arity = "0..1",
    description = "Description to generate a validation, 'detail:<name>' to show detail, or omit to list all"
  )
  private String description;

  @Option(names = {"--list", "-l"}, description = "List all indexed validations in this project")
  private boolean list;

  @Option(names = {"--detail", "-d"}, description = "Show source, when-to-use and how-to-use for a specific validation")
  private String detail;

  @Option(names = "--mode", defaultValue = "auto", description = "Generation mode: auto (default), json, java")
  private String mode;

  @Option(names = "--package",
    description = "Target package for a generated Java validator (default: the project's validation package)"
  )
  private String pkg;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = new HashMap<>();
    opts.put("mode", mode);
    if (pkg != null)
      opts.put("package", pkg);
    if (detail != null)
      opts.put("detail", detail);

    String input = "";
    if (list) {
      input = "--list";
    } else if (description != null) {
      input = description;
    }
    return CliSupport.print(new TestValidationSkill().execute(input, CliSupport.context(root, AgentMode.PATCH, opts)));
  }
}
