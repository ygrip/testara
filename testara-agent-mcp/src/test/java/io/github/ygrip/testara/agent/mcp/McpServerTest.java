package io.github.ygrip.testara.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir
  Path projectRoot;

  @Test
  void toolsListExposesAllTestaraUiModes() throws Exception {
    JsonNode response = new McpServer(projectRoot).handle(request("tools/list", "{}"));

    String json = response.toString();
    assertTrue(json.contains("\"testara_ui\""));
    assertTrue(json.contains("\"testara_debug\""));
    assertTrue(json.contains("explain | page | action | config | interactions"));
    assertTrue(json.contains("mode=interactions"));
    assertTrue(json.contains("\"format\""));
    assertTrue(json.contains("\"write\""));
  }

  @Test
  void testaraUiActionModeReturnsUserActionPreviewThroughMcp() throws Exception {
    JsonNode response = new McpServer(projectRoot).handle(request("tools/call", """
        {
          "name": "testara_ui",
          "arguments": {
            "mode": "action",
            "pageName": "login",
            "actionName": "login with credentials",
            "basePackage": "io.github.ygrip.automation"
          }
        }
        """));

    String text = response.at("/result/content/0/text").asText();
    assertTrue(text.contains("file_path: src/main/java/io/github/ygrip/automation/action/LoginActions.java"));
    assertTrue(text.contains("public class LoginActions extends UserAction"));
    assertTrue(text.contains("When user do \"login with credentials\" in \"login\" page with parameter"));
    assertTrue(text.contains("|key|value|"));
  }

  @Test
  void writeEnabledByDefaultWritesActionFileToDisk() throws Exception {
    JsonNode response = new McpServer(projectRoot).handle(request("tools/call", """
        {
          "name": "testara_ui",
          "arguments": {
            "mode": "action",
            "pageName": "login",
            "actionName": "login with credentials",
            "basePackage": "io.github.ygrip.automation",
            "write": true
          }
        }
        """));

    String text = response.at("/result/content/0/text").asText();
    // write is now enabled by default — should confirm file written, not write_disabled
    assertTrue(text.contains("written: src/main/java/io/github/ygrip/automation/action/LoginActions.java"));
  }

  private JsonNode request(String method, String paramsJson) throws Exception {
    return mapper.readTree("""
        {
          "jsonrpc": "2.0",
          "id": 1,
          "method": "%s",
          "params": %s
        }
        """.formatted(method, paramsJson));
  }
}
