package io.github.ygrip.testara.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.llm.DisabledLlmClient;
import io.github.ygrip.testara.agent.llm.LlmConfig;
import io.github.ygrip.testara.agent.llm.OpenAiLlmClient;
import io.github.ygrip.testara.agent.skill.*;
import io.github.ygrip.testara.agent.skill.run.TagExpressionResolver;
import io.github.ygrip.testara.agent.skill.run.MavenCommandBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * MCP server exposing all Testara Agent skills as MCP tools via stdio JSON-RPC 2.0.
 *
 * Start: java -jar testara-agent-mcp.jar [project-root]
 *
 * Security defaults:
 * - File writes disabled by default (TESTARA_AGENT_WRITE_ENABLED=false)
 * - Test execution disabled by default (TESTARA_AGENT_RUN_ENABLED=false)
 * - test_run defaults to dryRun=true
 */
public class McpServer {

  private static final Logger LOG = Logger.getLogger(McpServer.class.getName());

  private static final String SERVER_NAME    = "testara";
  private static final String SERVER_VERSION = "2.0.0";

  private final Path projectRoot;
  private final ObjectMapper mapper = new ObjectMapper();
  private volatile TestaraProjectProfile profile;

  // Skills
  private final TestSummarySkill    summarySkill    = new TestSummarySkill();
  private final TestOverviewSkill   overviewSkill   = new TestOverviewSkill();
  private final TestReviewSkill     reviewSkill     = new TestReviewSkill();
  private final TestRunSkill        runSkill        = new TestRunSkill(new TagExpressionResolver(), new MavenCommandBuilder());
  private final TestCommandSkill    commandSkill    = new TestCommandSkill();
  private final TestValidationSkill validationSkill = new TestValidationSkill();
  private final TestPlanSkill       planSkill       = new TestPlanSkill();
  private final TestInitSkill       initSkill       = new TestInitSkill();

  public McpServer(Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
  }

  public void run() throws IOException {
    BufferedReader in  = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

    // Eager index
    LOG.info("Indexing project at " + projectRoot);
    profile = JsonlKnowledgeStore.loadProfile(projectRoot);
    LOG.info("Index complete: " + profile.features().size() + " features, "
        + profile.totalScenarios() + " scenarios");

    String line;
    while ((line = in.readLine()) != null) {
      line = line.strip();
      if (line.isEmpty()) continue;
      try {
        JsonNode request  = mapper.readTree(line);
        JsonNode response = handle(request);
        out.println(mapper.writeValueAsString(response));
      } catch (Exception e) {
        LOG.warning("Error handling request: " + e.getMessage());
        out.println(errorResponse(null, -32700, "Parse error: " + e.getMessage()));
      }
    }
  }

  private JsonNode handle(JsonNode req) {
    String  id     = req.has("id") ? req.get("id").asText() : null;
    String  method = req.path("method").asText();
    JsonNode params = req.path("params");

    return switch (method) {
      case "initialize"    -> initializeResponse(id);
      case "tools/list"    -> toolsListResponse(id);
      case "tools/call"    -> toolsCallResponse(id, params);
      case "prompts/list"  -> promptsListResponse(id);
      case "prompts/get"   -> promptsGetResponse(id, params);
      case "notifications/initialized", "notifications/cancelled" -> mapper.createObjectNode();
      default -> {
        try { yield mapper.readTree(errorResponse(id, -32601, "Method not found: " + method)); }
        catch (Exception ex) { yield mapper.createObjectNode(); }
      }
    };
  }

  private JsonNode initializeResponse(String id) {
    ObjectNode result = mapper.createObjectNode();
    result.put("protocolVersion", "2024-11-05");
    result.putObject("capabilities").putObject("tools");
    result.with("capabilities").putObject("prompts");
    ObjectNode info = result.putObject("serverInfo");
    info.put("name", SERVER_NAME);
    info.put("version", SERVER_VERSION);
    return response(id, result);
  }

  private JsonNode toolsListResponse(String id) {
    ArrayNode tools = mapper.createArrayNode();
    tools.add(tool("testara_summary",    "Summarize feature files at scenario, feature, or directory level",
        requiredStr("path", "Path to .feature file or directory"),
        optionalStr("scenario", "Filter to specific scenario name")));
    tools.add(tool("testara_overview",   "Statistical overview of the test project",
        optionalStr("path", "Project root (default: .)"),
        optionalStr("format", "Output format: markdown or json")));
    tools.add(tool("testara_review",     "Review feature files for quality issues",
        requiredStr("path", "Path to .feature file or directory")));
    tools.add(tool("testara_run",        "Resolve natural-language test intent to tag expression, optionally execute",
        requiredStr("input", "Natural-language test run request"),
        optionalBool("dryRun", "Show plan only — default true"),
        optionalBool("execute", "Actually execute Maven — default false"),
        optionalStr("module", "Restrict to Maven module")));
    tools.add(tool("testara_command",    "Generate a Testara CommandLogic<T> class from description",
        requiredStr("description", "Description of the command"),
        optionalStr("package", "Target Java package"),
        optionalStr("returnType", "Return type")));
    tools.add(tool("testara_validation", "Generate a validation JSON or ValidatorLogic class",
        requiredStr("description", "Description of the validation"),
        optionalStr("mode", "auto, json, or java"),
        optionalStr("package", "Target Java package")));
    tools.add(tool("testara_plan",       "Generate a Testara-compatible Cucumber feature from intent",
        requiredStr("intent", "Test plan intent"),
        optionalStr("slice", "Layer: api, ui, database, streaming, fullstack"),
        optionalStr("domain", "Domain name override")));
    tools.add(tool("testara_init",       "Bootstrap or integrate a Testara automation project",
        optionalStr("type", "api, ui, database, streaming, fullstack"),
        optionalStr("basePackage", "Base Java package"),
        optionalStr("engine", "UI engine: selenium, playwright, appium"),
        optionalBool("integrateExisting", "Integrate into existing project")));

    ObjectNode result = mapper.createObjectNode();
    result.set("tools", tools);
    return response(id, result);
  }

  private JsonNode toolsCallResponse(String id, JsonNode params) {
    String toolName = params.path("name").asText();
    JsonNode args   = params.path("arguments");

    try {
      String content = dispatchTool(toolName, args);
      ObjectNode result = mapper.createObjectNode();
      ArrayNode  contentArr = result.putArray("content");
      contentArr.addObject().put("type", "text").put("text", content);
      return response(id, result);
    } catch (Exception e) {
      try {
        return mapper.readTree(errorResponse(id, -32000, "Tool execution error: " + e.getMessage()));
      } catch (Exception ex) {
        return mapper.createObjectNode();
      }
    }
  }

  private String dispatchTool(String name, JsonNode args) {
    AgentContext ctx = buildContext(name, args);
    return switch (name) {
      case "testara_summary" -> summarySkill.execute(
          new TestSummarySkill.Input(
              Paths.get(args.path("path").asText(".")),
              args.path("scenario").asText(null)), ctx);
      case "testara_overview" -> overviewSkill.execute(
          Paths.get(args.path("path").asText(".")), ctx);
      case "testara_review" -> reviewSkill.execute(
          Paths.get(args.path("path").asText(".")), ctx);
      case "testara_run" -> runSkill.execute(args.path("input").asText(""), ctx);
      case "testara_command" -> commandSkill.execute(args.path("description").asText(""), ctx);
      case "testara_validation" -> validationSkill.execute(args.path("description").asText(""), ctx);
      case "testara_plan" -> planSkill.execute(new TestPlanSkill.Input(
          args.path("intent").asText(""),
          args.path("slice").asText("api"),
          args.path("domain").asText(null),
          List.of()), ctx);
      case "testara_init" -> initSkill.execute(new TestInitSkill.Input(
          args.path("type").asText("api"),
          args.path("basePackage").asText("com.company.automation"),
          args.path("engine").asText("selenium"),
          args.path("integrateExisting").asBoolean(false)), ctx);
      default -> throw new IllegalArgumentException("Unknown tool: " + name);
    };
  }

  private AgentContext buildContext(String toolName, JsonNode args) {
    Map<String, String> opts = new LinkedHashMap<>();
    // Secure defaults: no execution, no writes
    opts.put("dryRun", args.path("dryRun").asBoolean(true) ? "true" : "false");
    opts.put("execute", args.path("execute").asBoolean(false) ? "true" : "false");
    if (args.has("format")) opts.put("format", args.path("format").asText("markdown"));
    if (args.has("mode"))   opts.put("mode",   args.path("mode").asText("auto"));
    if (args.has("package")) opts.put("package", args.path("package").asText());
    if (args.has("returnType")) opts.put("returnType", args.path("returnType").asText("String"));
    if (args.has("module"))  opts.put("module", args.path("module").asText());

    AgentMode mode = toolName.equals("testara_run") ? AgentMode.PLAN : AgentMode.READ_ONLY;

    LlmConfig cfg = LlmConfig.fromEnv();
    var llm = cfg.hasApiKey() ? new OpenAiLlmClient(cfg) : (io.github.ygrip.testara.agent.llm.LlmClient) new DisabledLlmClient();
    return new AgentContext(projectRoot, profile, mode, llm, opts);
  }

  // ── MCP helpers ───────────────────────────────────────────────────

  private ObjectNode tool(String name, String description, ObjectNode... properties) {
    ObjectNode tool   = mapper.createObjectNode();
    tool.put("name", name);
    tool.put("description", description);
    ObjectNode schema = tool.putObject("inputSchema");
    schema.put("type", "object");
    ObjectNode props  = schema.putObject("properties");
    ArrayNode  required = schema.putArray("required");
    for (ObjectNode prop : properties) {
      String propName = prop.path("_name").asText();
      boolean isRequired = prop.path("_required").asBoolean(false);
      prop.remove("_name"); prop.remove("_required");
      props.set(propName, prop);
      if (isRequired) required.add(propName);
    }
    return tool;
  }

  private ObjectNode requiredStr(String name, String desc) {
    ObjectNode n = mapper.createObjectNode();
    n.put("_name", name); n.put("_required", true);
    n.put("type", "string"); n.put("description", desc);
    return n;
  }

  private ObjectNode optionalStr(String name, String desc) {
    ObjectNode n = mapper.createObjectNode();
    n.put("_name", name); n.put("_required", false);
    n.put("type", "string"); n.put("description", desc);
    return n;
  }

  private ObjectNode optionalBool(String name, String desc) {
    ObjectNode n = mapper.createObjectNode();
    n.put("_name", name); n.put("_required", false);
    n.put("type", "boolean"); n.put("description", desc);
    return n;
  }

  private ObjectNode response(String id, JsonNode result) {
    ObjectNode r = mapper.createObjectNode();
    r.put("jsonrpc", "2.0");
    if (id != null) r.put("id", id);
    r.set("result", result);
    return r;
  }

  private String errorResponse(String id, int code, String message) {
    try {
      ObjectNode r = mapper.createObjectNode();
      r.put("jsonrpc", "2.0");
      if (id != null) r.put("id", id);
      ObjectNode err = r.putObject("error");
      err.put("code", code);
      err.put("message", message);
      return mapper.writeValueAsString(r);
    } catch (Exception e) {
      return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Internal error\"}}";
    }
  }

  // ── Prompts ───────────────────────────────────────────────────────

  private JsonNode promptsListResponse(String id) {
    ArrayNode prompts = mapper.createArrayNode();
    prompts.add(prompt("test-summary",
        "Summarize feature files at scenario, feature, or directory level",
        requiredPromptArg("path", "Path to .feature file or directory"),
        optionalPromptArg("scenario", "Filter to specific scenario name")));
    prompts.add(prompt("test-review",
        "Review feature files for quality issues — duplicates, weak assertions, complexity",
        requiredPromptArg("path", "Path to .feature file or directory")));
    prompts.add(prompt("test-plan",
        "Generate a Testara-compatible Cucumber feature from user intent",
        requiredPromptArg("intent", "Test plan intent description"),
        optionalPromptArg("slice", "Layer: api, ui, database, streaming")));
    prompts.add(prompt("test-command",
        "Generate a Testara CommandLogic<T> Java class from description",
        requiredPromptArg("description", "Description of the command to generate")));
    prompts.add(prompt("test-validation",
        "Generate a validation JSON or custom ValidatorLogic Java class",
        requiredPromptArg("description", "Description of the validation to generate")));
    prompts.add(prompt("test-init",
        "Bootstrap or integrate a Testara automation project",
        optionalPromptArg("type", "Project type: api, ui, database, streaming, fullstack")));
    prompts.add(prompt("test-overview",
        "Statistical overview of the test project",
        optionalPromptArg("path", "Project root directory")));
    prompts.add(prompt("test-run",
        "Resolve natural-language test intent into Cucumber tag expression and run tests",
        requiredPromptArg("input", "Natural language test run request"),
        optionalPromptArg("dryRun", "Show plan only without execution")));

    ObjectNode result = mapper.createObjectNode();
    result.set("prompts", prompts);
    return response(id, result);
  }

  private JsonNode promptsGetResponse(String id, JsonNode params) {
    String promptName = params.path("name").asText();
    String description = switch (promptName) {
      case "test-summary" -> "Use Testara to summarize tests in the specified path.";
      case "test-review" -> "Use Testara to review test quality in the specified path.";
      case "test-plan" -> "Use Testara to generate a test plan for: <describe the intent here>.";
      case "test-command" -> "Use Testara to generate a command for: <describe the command here>.";
      case "test-validation" -> "Use Testara to generate a validation for: <describe it here>.";
      case "test-init" -> "Use Testara to bootstrap a new automation project or integrate into existing.";
      case "test-overview" -> "Use Testara to generate a test overview report for this project.";
      case "test-run" -> "Use Testara to dry-run this test selection: <describe tests to run>.";
      default -> "Use Testara to assist with test automation.";
    };
    ObjectNode result = mapper.createObjectNode();
    ArrayNode messages = result.putArray("messages");
    messages.addObject()
        .put("role", "user")
        .putObject("content")
        .put("type", "text")
        .put("text", description);
    return response(id, result);
  }

  private ObjectNode prompt(String name, String description, ObjectNode... args) {
    ObjectNode p = mapper.createObjectNode();
    p.put("name", name);
    p.put("description", description);
    ArrayNode arguments = p.putArray("arguments");
    for (ObjectNode arg : args) {
      arg.remove("_name"); arg.remove("_required");
      arguments.add(arg);
    }
    return p;
  }

  private ObjectNode requiredPromptArg(String name, String desc) {
    ObjectNode n = mapper.createObjectNode();
    n.put("_name", name); n.put("_required", true);
    n.put("name", name); n.put("description", desc);
    n.put("required", true);
    return n;
  }

  private ObjectNode optionalPromptArg(String name, String desc) {
    ObjectNode n = mapper.createObjectNode();
    n.put("_name", name); n.put("_required", false);
    n.put("name", name); n.put("description", desc);
    n.put("required", false);
    return n;
  }
}
