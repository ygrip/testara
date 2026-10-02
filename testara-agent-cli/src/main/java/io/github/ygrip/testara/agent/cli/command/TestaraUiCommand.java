package io.github.ygrip.testara.agent.cli.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestaraUiSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "/testara-ui",
  aliases = {"testara-ui"},
  description = "Generate Testara UI artifacts: Page class, UserAction class, engine config, or interaction catalog",
  mixinStandardHelpOptions = true
)
public class TestaraUiCommand implements Callable<Integer> {

  @Option(names = "--mode",
    defaultValue = "explain",
    description = "explain (default), page, action, config, interactions, validate-page"
  )
  private String mode;

  @Option(names = "--page-name", description = "Page name, e.g. login")
  private String pageName;

  @Option(names = "--action-name", description = "Action description, e.g. 'login with credential'")
  private String actionName;

  @Option(names = "--engine", description = "UI engine: selenium, playwright, appium, vibium")
  private String engine;

  @Option(names = "--base-package", description = "Base Java package (inferred from the project if not set)")
  private String basePackage;

  @Option(names = "--html-snapshot", description = "Rendered HTML file for mode validate-page selector checks")
  private Path htmlSnapshot;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Option(names = "--write", defaultValue = "false", description = "Write the generated file to disk")
  private boolean write;

  @Option(names = "--overwrite", defaultValue = "false", description = "Replace files that already exist")
  private boolean overwrite;

  @Option(names = "--compile", defaultValue = "false", description = "Run the test-compile gate after writing")
  private boolean compile;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = CliSupport.projectOptions(root);
    String blocked = CliSupport.requestWrite("testara-ui", opts, write);
    if (blocked != null) {
      return CliSupport.print(blocked);
    }
    if (overwrite) {
      opts.put("overwrite", "true");
    }
    if (compile) {
      opts.put("compile", "true");
    }
    String html = null;
    if (htmlSnapshot != null) {
      try {
        html = Files.readString(htmlSnapshot, StandardCharsets.UTF_8);
      } catch (IOException e) {
        return CliSupport.print("error: cannot read --html-snapshot " + htmlSnapshot + ": " + e.getMessage());
      }
    }
    AgentMode agentMode = AgentMode.READ_ONLY;
    if (write) {
      agentMode = AgentMode.APPLY;
    }
    return CliSupport.print(new TestaraUiSkill().execute(
      new TestaraUiSkill.Input(mode, pageName, actionName, engine, basePackage, html),
      CliSupport.contextWithoutIndex(root, agentMode, opts)
    ));
  }
}
