package io.github.ygrip.testara.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    assertTrue(json.contains("\"testara_index\""));
    assertTrue(json.contains("\"testara_debug\""));
    assertTrue(json.contains("Run tests by explicit Cucumber tag expression"));
    assertTrue(json.contains("default true"));
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

  @Test
  void initializeReportsThisModulesBuildVersion() throws Exception {
    JsonNode response = new McpServer(projectRoot).handle(request("initialize", "{}"));

    assertEquals(1, response.path("id").asInt());
    assertEquals("testara", response.at("/result/serverInfo/name").asText());
    assertTrue(response.at("/result/serverInfo/version").asText().matches("\\d+\\.\\d+\\.\\d+(?:[-+].+)?"));
  }

  @Test
  void messagesWithoutIdNeverGetAResponse() {
    McpServer server = new McpServer(projectRoot);

    assertNull(server.handleLine("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    assertNull(server.handleLine("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/roots/list_changed\"}"));
    assertNull(server.handleLine("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}"));
  }

  @Test
  void pingReturnsEmptyResult() throws Exception {
    JsonNode response = mapper.readTree(new McpServer(projectRoot)
        .handleLine("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}"));

    assertEquals(7, response.path("id").asInt());
    assertTrue(response.path("result").isObject());
    assertEquals(0, response.path("result").size());
  }

  @Test
  void toolsListDeclaresEveryToolWithAnObjectSchema() throws Exception {
    JsonNode tools = new McpServer(projectRoot).handle(request("tools/list", "{}")).at("/result/tools");

    List<String> expected = List.of("testara_summary", "testara_overview", "testara_review", "testara_run",
        "testara_index", "testara_command", "testara_command_detail", "testara_validation",
        "testara_validation_detail", "testara_plan", "testara_guide", "testara_context", "testara_property",
        "testara_api", "testara_ui", "testara_bootstrap", "testara_db", "testara_validate", "testara_debug",
        "testara_init");
    assertEquals(expected.size(), tools.size());
    for (String name : expected) {
      JsonNode tool = find(tools, name);
      assertEquals("object", tool.at("/inputSchema/type").asText(), name);
      assertTrue(tool.at("/inputSchema/properties").isObject(), name);
    }
    JsonNode run = find(tools, "testara_run").at("/inputSchema/properties");
    assertEquals("integer", run.at("/timeoutMinutes/type").asText());
    assertEquals("boolean", run.at("/rerunFailed/type").asText());
    assertTrue(run.has("gradleTask"));
    assertTrue(run.has("format"));
    for (String name : List.of("testara_plan", "testara_ui", "testara_bootstrap")) {
      JsonNode properties = find(tools, name).at("/inputSchema/properties");
      assertTrue(properties.has("overwrite"), name);
      assertTrue(properties.has("compile"), name);
    }
    JsonNode api = find(tools, "testara_api").at("/inputSchema/properties");
    assertTrue(api.has("write"));
    assertTrue(api.has("overwrite"));
  }

  @Test
  void unknownToolIsInvalidParams() throws Exception {
    JsonNode response = new McpServer(projectRoot).handle(request("tools/call", """
        {"name": "testara_nope", "arguments": {}}
        """));

    assertEquals(1, response.path("id").asInt());
    assertEquals(-32602, response.at("/error/code").asInt());
    assertTrue(response.at("/error/message").asText().contains("testara_nope"));
  }

  @Test
  void failingToolReturnsIsErrorResult() throws Exception {
    JsonNode response = new McpServer(projectRoot).handle(request("tools/call", """
        {"name": "testara_summary", "arguments": {"path": "bad\\u0000path"}}
        """));

    assertFalse(response.has("error"));
    assertTrue(response.at("/result/isError").asBoolean());
    assertTrue(response.at("/result/content/0/text").asText().startsWith("Tool execution error"));
  }

  @Test
  void malformedJsonIsParseErrorWithNullId() throws Exception {
    JsonNode response = mapper.readTree(new McpServer(projectRoot).handleLine("{\"jsonrpc\": \"2.0\", \"id\": "));

    assertEquals(-32700, response.at("/error/code").asInt());
    assertTrue(response.has("id"));
    assertTrue(response.path("id").isNull());
  }

  @Test
  void yamlWriteDisabledBlocksExplicitWrite() throws Exception {
    Files.writeString(projectRoot.resolve("testara-agent.yaml"), "write:\n  enabled: false\n");

    String text = new McpServer(projectRoot).handle(request("tools/call", uiActionCall(", \"write\": true")))
        .at("/result/content/0/text").asText();

    assertTrue(text.startsWith("write_disabled: write.enabled: false"), text);
    assertFalse(Files.exists(projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java")));
  }

  @Test
  void yamlScalarWriteFalseBlocksExplicitWrite() throws Exception {
    Files.writeString(projectRoot.resolve("testara-agent.yaml"), "write: false\n");

    String text = new McpServer(projectRoot).handle(request("tools/call", uiActionCall(", \"write\": true")))
        .at("/result/content/0/text").asText();

    assertTrue(text.startsWith("write_disabled:"), text);
    assertFalse(Files.exists(projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java")));
  }

  @Test
  void yamlNeverGrantsWritesWithoutExplicitArgument() throws Exception {
    Files.writeString(projectRoot.resolve("testara-agent.yaml"), "write: true\nrun:\n  write: true\n");

    String text = new McpServer(projectRoot).handle(request("tools/call", uiActionCall("")))
        .at("/result/content/0/text").asText();

    assertFalse(text.contains("written:"), text);
    assertFalse(Files.exists(projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java")));
  }

  @Test
  void runOptionsReachTheRunSkill() throws Exception {
    String text = new McpServer(projectRoot).handle(request("tools/call", """
        {"name": "testara_run", "arguments": {"input": "@smoke", "timeoutMinutes": 0}}
        """)).at("/result/content/0/text").asText();

    assertTrue(text.contains("timeoutMinutes must be a positive whole number"), text);
  }

  private String uiActionCall(String extraArguments) {
    return """
        {
          "name": "testara_ui",
          "arguments": {
            "mode": "action",
            "pageName": "login",
            "actionName": "login with credentials",
            "basePackage": "io.github.ygrip.automation"%s
          }
        }
        """.formatted(extraArguments);
  }

  private JsonNode find(JsonNode tools, String name) {
    for (JsonNode tool : tools) {
      if (name.equals(tool.path("name").asText())) return tool;
    }
    throw new AssertionError("Tool not listed: " + name);
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
