package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.config.AgentYamlConfig;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.mcp.WriteGate;
import io.github.ygrip.testara.agent.skill.AgentContext;

/**
 * Shared plumbing for the skill subcommands: project options, the write gate, skill contexts and
 * exit codes.
 * <p>
 * Exit codes: {@value #OK} success, {@value #FAILED} test run failed, timed out or preflight failed,
 * {@value #BLOCKED} blocked (writes disabled, execution not allowed) or invalid/missing input.
 */
final class CliSupport {

  static final int OK = 0;
  static final int FAILED = 1;
  static final int BLOCKED = 2;

  /** Output prefixes the skills use when they need more input, refuse, or cannot proceed. */
  private static final List<String> BLOCKED_MARKERS = List.of("needs_input:", "write_disabled:", "error:",
    "No feature files found");

  private CliSupport() {}

  static Path root(Path projectRoot) {
    return projectRoot.toAbsolutePath()
      .normalize();
  }

  /** Options from the project's {@code testara-agent.yaml}, before CLI flags are applied. */
  static Map<String, String> projectOptions(Path root) {
    Map<String, String> opts = new HashMap<>();
    AgentYamlConfig.load(root)
      .apply(opts);
    return opts;
  }

  /**
   * Applies the {@code --write} flag: the YAML file never enables writes, so only the flag sets
   * {@code write}. Returns the write_disabled message when the environment or YAML turns writes off
   * and a write was requested, otherwise null.
   */
  static String requestWrite(String command, Map<String, String> opts, boolean write) {
    String disabled = WriteGate.disabledMessage(command, opts);
    opts.remove("write");
    if (!write) {
      return null;
    }
    if (disabled != null) {
      return disabled;
    }
    opts.put("write", "true");
    return null;
  }

  /** Context with the project index loaded (creates {@code .testara-agent/} under {@code root}). */
  static AgentContext context(Path root, AgentMode mode, Map<String, String> opts) {
    return new AgentContext(root, JsonlKnowledgeStore.loadProfile(root), mode, new DisabledLlmClient(), opts);
  }

  /** Context for skills that never read the project index; nothing is indexed or cached. */
  static AgentContext contextWithoutIndex(Path root, AgentMode mode, Map<String, String> opts) {
    return new AgentContext(root, null, mode, new DisabledLlmClient(), opts);
  }

  /** Prints a skill output and returns its exit code. */
  static int print(String output) {
    System.out.println(output);
    return exitCode(output);
  }

  static int exitCode(String output) {
    String text = output.stripLeading();
    for (String marker : BLOCKED_MARKERS) {
      if (text.startsWith(marker)) {
        return BLOCKED;
      }
    }
    return OK;
  }
}
