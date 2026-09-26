package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestPlanSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "/test-plan",
  aliases = {"test-plan"},
  description = "Generate a Testara-compatible Cucumber feature from user intent",
  mixinStandardHelpOptions = true
)
public class TestPlanCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Intent, e.g. 'Create tests for refund approval flow'")
  private String intent;

  @Option(names = "--slice",
    description = "Layer slice: api, ui, database, streaming, fullstack (inferred from the intent if not set)"
  )
  private String slice;

  @Option(names = "--domain", description = "Domain name override (auto-inferred if not set)")
  private String domain;

  @Option(names = "--tag", description = "Extra tags to add", arity = "0..*")
  private List<String> tags;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Option(names = "--write",
    defaultValue = "false",
    description = "Write the generated feature file to disk at the resolved placement path"
  )
  private boolean write;

  @Option(names = "--overwrite", defaultValue = "false", description = "Replace files that already exist")
  private boolean overwrite;

  @Option(names = "--compile", defaultValue = "false", description = "Run the test-compile gate after writing")
  private boolean compile;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = CliSupport.projectOptions(root);
    String blocked = CliSupport.requestWrite("test-plan", opts, write);
    if (blocked != null) {
      return CliSupport.print(blocked);
    }
    if (overwrite) {
      opts.put("overwrite", "true");
    }
    if (compile) {
      opts.put("compile", "true");
    }
    AgentMode mode = AgentMode.PATCH;
    if (write) {
      mode = AgentMode.APPLY;
    }
    List<String> extraTags = List.of();
    if (tags != null) {
      extraTags = tags;
    }
    String output = new TestPlanSkill().execute(
      new TestPlanSkill.Input(intent, slice, domain, extraTags), CliSupport.context(root, mode, opts)
    );
    return CliSupport.print(output, TestPlanSkill::exitCode);
  }
}
