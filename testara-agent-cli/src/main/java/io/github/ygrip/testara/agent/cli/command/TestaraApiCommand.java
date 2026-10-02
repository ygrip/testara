package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.skill.TestaraApiSkill;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "/testara-api",
  aliases = {"testara-api"},
  description = "Explain API config, generate an api.service config block, or generate a request spec JSON",
  mixinStandardHelpOptions = true
)
public class TestaraApiCommand implements Callable<Integer> {

  @Option(names = "--mode", defaultValue = "explain", description = "explain (default), config, request-spec")
  private String mode;

  @Option(names = "--domain", description = "Service/domain name")
  private String domain;

  @Option(names = "--flow", description = "Request spec flow name")
  private String flow;

  @Option(names = "--method", description = "HTTP method: GET, POST, PUT, PATCH, DELETE")
  private String method;

  @Option(names = "--endpoint", description = "Endpoint URL or path")
  private String endpoint;

  @Option(names = "--project", defaultValue = ".", description = "Project root")
  private Path projectRoot;

  @Option(names = "--write", defaultValue = "false", description = "Write the config properties or request spec")
  private boolean write;

  @Option(names = "--overwrite", defaultValue = "false", description = "Replace a request spec that already exists")
  private boolean overwrite;

  @Override
  public Integer call() {
    Path root = CliSupport.root(projectRoot);
    Map<String, String> opts = CliSupport.projectOptions(root);
    String blocked = CliSupport.requestWrite("testara-api", opts, write);
    if (blocked != null) {
      return CliSupport.print(blocked);
    }
    if (overwrite) {
      opts.put("overwrite", "true");
    }
    AgentMode agentMode = AgentMode.READ_ONLY;
    if (write) {
      agentMode = AgentMode.APPLY;
    }
    return CliSupport.print(new TestaraApiSkill().execute(
      new TestaraApiSkill.Input(mode, domain, flow, method, endpoint),
      CliSupport.contextWithoutIndex(root, agentMode, opts)
    ));
  }
}
