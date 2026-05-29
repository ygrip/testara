package io.github.ygrip.testara.agent.mcp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestaraAgentMcpMain {

  public static void main(String[] args) throws IOException {
    Path projectRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
    new McpServer(projectRoot).run();
  }
}
