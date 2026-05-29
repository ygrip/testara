package io.github.ygrip.testara.agent.cli.command;

import io.github.ygrip.testara.agent.mcp.McpServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "mcp",
    description = "Start the MCP stdio server — exposes all Testara skills as MCP tools",
    mixinStandardHelpOptions = true)
public class McpCommand implements Runnable {

  @Parameters(index = "0", defaultValue = ".", description = "Project root to index (default: current directory)")
  private Path projectRoot;

  @Override
  public void run() {
    Path root = projectRoot.toAbsolutePath().normalize();
    System.err.println("[testara-agent] Starting MCP server for project: " + root);
    try {
      new McpServer(root).run();
    } catch (Exception e) {
      System.err.println("[testara-agent] MCP server error: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
