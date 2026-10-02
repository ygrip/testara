package io.github.ygrip.testara.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerTest {

  private static final Pattern RUN_ID = Pattern.compile("run_started: (\\S+)");
  private static final String PASSING_REPORT = """
      [{"uri": "features/login.feature", "elements": [
        {"type": "scenario", "keyword": "Scenario", "name": "user logs in",
         "steps": [{"result": {"status": "passed"}}]}]}]
      """;
  private static final String LONG_BUILD = """
      sleep 60 &
      echo $! > child.pid
      wait
      """;

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
        "testara_init", "testara_run_status", "testara_run_cancel");
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
    assertEquals("boolean", run.at("/wait/type").asText());
    JsonNode status = find(tools, "testara_run_status").at("/inputSchema");
    assertEquals("integer", status.at("/properties/waitSeconds/type").asText());
    assertEquals("runId", status.at("/required/0").asText());
    assertEquals("runId", find(tools, "testara_run_cancel").at("/inputSchema/required/0").asText());
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

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void executedRunStartsInTheBackgroundAndStatusLongPollsToTheVerdict() throws Exception {
    runnableProject("""
        echo "building"
        sleep 2
        mkdir -p target/destination
        cp passing.json target/destination/cucumber.json
        exit 0
        """);
    McpServer server = new McpServer(projectRoot);

    long start = System.currentTimeMillis();
    String started = callText(server, "testara_run", "{\"input\": \"@smoke\"}");
    assertTrue(System.currentTimeMillis() - start < 2_000, "testara_run must not wait for the build");
    String runId = runId(started);
    assertTrue(started.contains("state: RUNNING"), started);
    assertTrue(started.contains("testara_run_status {\"runId\":\"" + runId + "\",\"waitSeconds\":30}"), started);

    String running = callText(server, "testara_run_status", "{\"runId\": \"" + runId + "\"}");
    assertTrue(running.contains("state: RUNNING"), running);
    assertTrue(running.contains("log-tail (last 30 lines):"), running);

    String finished = callText(server, "testara_run_status", "{\"runId\": \"" + runId + "\", \"waitSeconds\": 30}");
    assertTrue(finished.contains("state: PASSED"), finished);
    assertTrue(finished.contains("exit-code: 0"), finished);
    assertTrue(finished.contains("**Status:** PASSED"), finished);

    JsonNode json = mapper.readTree(callText(server, "testara_run_status",
        "{\"runId\": \"" + runId + "\", \"format\": \"json\"}"));
    assertEquals("PASSED", json.path("state").asText());
    assertEquals(0, json.path("exitCode").asInt());
    assertTrue(json.path("result").asText().contains("**Status:** PASSED"));

    JsonNode metadata = mapper.readTree(projectRoot.resolve(".testara-agent/runs/" + runId + ".json").toFile());
    assertEquals("PASSED", metadata.path("state").asText());
    assertFalse(metadata.path("finishedAt").isNull());
    String recorded = callText(new McpServer(projectRoot), "testara_run_status", "{\"runId\": \"" + runId + "\"}");
    assertTrue(recorded.contains("state: PASSED"), recorded);
    assertTrue(recorded.contains("not tracking the run"), recorded);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void cancelKillsTheRunAndSecondRunForTheProjectIsInProgress() throws Exception {
    runnableProject(LONG_BUILD);
    McpServer server = new McpServer(projectRoot);

    String runId = runId(callText(server, "testara_run", "{\"input\": \"@smoke\"}"));
    long childPid = awaitPid(projectRoot.resolve("child.pid"));
    String second = callText(server, "testara_run", "{\"input\": \"@smoke\"}");
    String plan = callText(server, "testara_run", "{\"input\": \"@smoke\", \"dryRun\": true}");
    String cancelled = callText(server, "testara_run_cancel", "{\"runId\": \"" + runId + "\"}");
    String again = callText(server, "testara_run_cancel", "{\"runId\": \"" + runId + "\"}");

    assertTrue(second.startsWith("run_in_progress: " + runId), second);
    assertTrue(plan.contains("**Resolved tag expression:** `@smoke`"), plan);
    assertTrue(cancelled.contains("state: CANCELLED"), cancelled);
    assertTrue(cancelled.contains("**Status:** CANCELLED"), cancelled);
    assertTrue(again.contains("state: CANCELLED"), again);
    assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false), "build tree must be killed");
    String next = callText(server, "testara_run", "{\"input\": \"@smoke\"}");
    assertTrue(next.startsWith("run_started: "), "a finished run frees the project: " + next);
    callText(server, "testara_run_cancel", "{\"runId\": \"" + runId(next) + "\"}");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void waitTrueBlocksUntilTheRunFinishes() throws Exception {
    runnableProject("""
        echo "[ERROR] BUILD FAILURE"
        exit 1
        """);

    String text = callText(new McpServer(projectRoot), "testara_run", "{\"input\": \"@smoke\", \"wait\": true}");

    assertFalse(text.contains("run_started"), text);
    assertTrue(text.contains("**Status:** FAILED"), text);
    assertTrue(text.contains("**Exit code:** 1"), text);
  }

  @Test
  void unknownRunIdIsAnErrorResult() throws Exception {
    McpServer server = new McpServer(projectRoot);

    for (String tool : List.of("testara_run_status", "testara_run_cancel")) {
      JsonNode response = server.handle(request("tools/call", """
          {"name": "%s", "arguments": {"runId": "nope"}}
          """.formatted(tool)));

      assertTrue(response.at("/result/isError").asBoolean(), tool);
      assertEquals("unknown_run: nope", response.at("/result/content/0/text").asText(), tool);
    }
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void pingIsAnsweredWhileARunIsActiveAndCancellingTheRequestCancelsTheRun() throws Exception {
    runnableProject(LONG_BUILD);
    McpServer server = new McpServer(projectRoot);
    PipedOutputStream stdin = new PipedOutputStream();
    PipedInputStream serverIn = new PipedInputStream(stdin, 1 << 16);
    LineQueue stdout = new LineQueue();
    CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
      try {
        server.run(serverIn, stdout);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    });
    Map<Integer, JsonNode> responses = new HashMap<>();

    send(stdin, """
        {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"testara_run","arguments":{"input":"@smoke","wait":true}}}""");
    long childPid = awaitPid(projectRoot.resolve("child.pid"));
    send(stdin, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}");
    JsonNode ping = awaitResponse(stdout, responses, 2);
    assertTrue(ping.path("result").isObject());
    assertTrue(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false), "run must still be active");

    send(stdin, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":1}}");
    long deadline = System.currentTimeMillis() + 20_000;
    while (ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false)) {
      assertTrue(System.currentTimeMillis() < deadline, "cancelling the request must kill the build");
      Thread.sleep(20);
    }
    stdin.close();
    served.get(60, TimeUnit.SECONDS);

    List<String> rest = new ArrayList<>();
    stdout.lines.drainTo(rest);
    for (String line : rest) {
      assertFalse(mapper.readTree(line).path("id").asInt() == 1, "a cancelled request gets no response: " + line);
    }
    assertFalse(responses.containsKey(1));
  }

  @Test
  void lineWriterNeverInterleavesConcurrentLines() throws Exception {
    StringWriter buffer = new StringWriter();
    McpServer.LineWriter writer = new McpServer.LineWriter(new PrintWriter(buffer, true));
    String payload = "x".repeat(4_000);
    List<CompletableFuture<Void>> writers = new ArrayList<>();
    for (int thread = 0; thread < 8; thread++) {
      int id = thread;
      writers.add(CompletableFuture.runAsync(() -> {
        for (int i = 0; i < 200; i++) {
          writer.write("{\"id\":" + id + ",\"n\":" + i + ",\"text\":\"" + payload + "\"}");
        }
      }));
    }
    CompletableFuture.allOf(writers.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

    List<String> lines = buffer.toString().lines().toList();
    assertEquals(1_600, lines.size());
    for (String line : lines) {
      assertEquals(payload, mapper.readTree(line).path("text").asText());
    }
  }

  private void runnableProject(String mvnwBody) throws IOException {
    Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
    Path features = Files.createDirectories(projectRoot.resolve("src/test/resources/features"));
    Files.writeString(features.resolve("login.feature"), """
        @smoke
        Feature: Login

          Scenario: user logs in
            Given user open "login" page
            Then user should see "welcome" is displayed
        """);
    Files.writeString(projectRoot.resolve("passing.json"), PASSING_REPORT);
    Path mvnw = projectRoot.resolve("mvnw");
    Files.writeString(mvnw, "#!/bin/sh\n" + mvnwBody);
    assertTrue(mvnw.toFile().setExecutable(true));
  }

  private String callText(McpServer server, String tool, String arguments) throws Exception {
    JsonNode response = server.handle(request("tools/call", """
        {"name": "%s", "arguments": %s}
        """.formatted(tool, arguments)));
    assertFalse(response.at("/result/isError").asBoolean(), response.toString());
    return response.at("/result/content/0/text").asText();
  }

  private String runId(String started) {
    Matcher matcher = RUN_ID.matcher(started);
    assertTrue(matcher.find(), started);
    return matcher.group(1);
  }

  private long awaitPid(Path pidFile) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    while (!Files.isRegularFile(pidFile) || Files.readString(pidFile).isBlank()) {
      assertTrue(System.currentTimeMillis() < deadline, "child pid was never written");
      Thread.sleep(20);
    }
    return Long.parseLong(Files.readString(pidFile).strip());
  }

  private void send(PipedOutputStream stdin, String line) throws IOException {
    stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
    stdin.flush();
  }

  private JsonNode awaitResponse(LineQueue stdout, Map<Integer, JsonNode> responses, int id) throws Exception {
    while (!responses.containsKey(id)) {
      String line = stdout.lines.poll(20, TimeUnit.SECONDS);
      assertNotNull(line, "no response for request " + id);
      JsonNode response = mapper.readTree(line);
      responses.put(response.path("id").asInt(), response);
    }
    return responses.get(id);
  }

  /** Collects complete stdout lines written by the server. */
  private static final class LineQueue extends OutputStream {

    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    @Override
    public synchronized void write(int b) {
      if (b == '\n') {
        lines.add(line.toString(StandardCharsets.UTF_8));
        line.reset();
      } else {
        line.write(b);
      }
    }
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
