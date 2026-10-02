package io.github.ygrip.testara.agent.mcp;

import java.util.Map;

/**
 * Hard off switch for file writes, shared by the MCP server and the CLI.
 * <p>
 * Writes are on by default. {@code TESTARA_AGENT_WRITE_ENABLED=false} in the environment, or
 * {@code write.enabled: false} in the project's {@code testara-agent.yaml}, turns them off, and no
 * per-call argument or CLI flag can turn them back on. The YAML file can never turn writes on:
 * only an explicit call argument or CLI flag requests a write.
 */
public final class WriteGate {

  public static final String WRITE_ENABLED_ENV = "TESTARA_AGENT_WRITE_ENABLED";

  private WriteGate() {}

  /**
   * Returns the {@code write_disabled} message when a switch turns writes off, otherwise null.
   *
   * @param toolName tool or command name reported in the message
   * @param projectOptions options after {@code AgentYamlConfig.apply}, before call arguments are added
   */
  public static String disabledMessage(String toolName, Map<String, String> projectOptions) {
    if ("false".equalsIgnoreCase(System.getenv(WRITE_ENABLED_ENV))) {
      return message(toolName, WRITE_ENABLED_ENV + "=false is set in the environment",
          "remove " + WRITE_ENABLED_ENV + "=false from the environment to re-enable writes");
    }
    if ("false".equals(projectOptions.get("write"))) {
      return message(toolName, "write.enabled: false is set in testara-agent.yaml",
          "remove write.enabled: false from the project's testara-agent.yaml to re-enable writes");
    }
    return null;
  }

  private static String message(String toolName, String reason, String nextStep) {
    return """
        write_disabled: %s
        capability_available: %s can preview artifacts — call it without write/createFiles (CLI: drop --write, or pass --preview to test-init) to get file_path/source
        next_step: %s
        """.formatted(reason, toolName, nextStep);
  }
}
