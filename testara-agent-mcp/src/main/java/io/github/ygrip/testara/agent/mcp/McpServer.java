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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * MCP server exposing all Testara Agent skills as MCP tools via stdio JSON-RPC 2.0.
 *
 * Start: java -jar testara-agent-mcp.jar [project-root]
 *
 * Defaults:
 * - File writes enabled by default (set TESTARA_AGENT_WRITE_ENABLED=false to disable)
 * - Test execution disabled by default (TESTARA_AGENT_RUN_ENABLED=false)
 * - test_run defaults to dryRun=true
 */
public class McpServer {

  private static final Logger LOG = Logger.getLogger(McpServer.class.getName());

  private static final String SERVER_NAME    = "testara";
  private static final String SERVER_VERSION = readVersion();

  private static String readVersion() {
    try (java.io.InputStream is = McpServer.class.getResourceAsStream(
        "/META-INF/maven/io.github.ygrip/testara-agent-cli/pom.properties")) {
      if (is != null) {
        java.util.Properties p = new java.util.Properties();
        p.load(is);
        return p.getProperty("version", "unknown");
      }
    } catch (Exception ignored) {}
    return "unknown";
  }

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
  private final ListCommandsSkill   listCommandsSkill    = new ListCommandsSkill();
  private final ListValidationsSkill listValidationsSkill = new ListValidationsSkill();
  private final TestaraContextSkill  contextSkill  = new TestaraContextSkill();
  private final TestaraGuideSkill    guideSkill    = new TestaraGuideSkill();
  private final TestaraPropertySkill propertySkill = new TestaraPropertySkill();
  private final TestaraApiSkill      apiSkill      = new TestaraApiSkill();
  private final TestaraUiSkill       uiSkill       = new TestaraUiSkill();
  private final TestaraBootstrapSkill bootstrapSkill  = new TestaraBootstrapSkill();
  private final TestaraDbSkill        dbSkill         = new TestaraDbSkill();
  private final TestaraDebugSkill     debugSkill      = new TestaraDebugSkill();
  private final TestaraValidateSkill  validateSkill   = new TestaraValidateSkill();

  public McpServer(Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
  }

  public void run() throws IOException {
    BufferedReader in  = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

    // Index only when the root looks like a real project — skip when launched from home dir or
    // a non-project directory (e.g. VS Code global MCP config with "." as CWD)
    if (Files.exists(projectRoot.resolve("pom.xml")) || Files.exists(projectRoot.resolve("build.gradle"))) {
      LOG.info("Indexing project at " + projectRoot);
      profile = JsonlKnowledgeStore.loadProfile(projectRoot);
      LOG.info("Index complete: " + profile.features().size() + " features, "
          + profile.totalScenarios() + " scenarios");
    } else {
      LOG.warning("No pom.xml or build.gradle found at " + projectRoot + " — starting without project index. Pass the project path as the mcp argument.");
      profile = new TestaraProjectProfile(projectRoot, null, "unknown",
          List.of(), List.of(), List.of(), List.of(),
          List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
          Map.of(), Map.of(), List.of(), List.of());
    }

    String line;
    while ((line = in.readLine()) != null) {
      line = line.strip();
      if (line.isEmpty()) continue;
      try {
        JsonNode request  = mapper.readTree(line);
        JsonNode response = handle(request);
        if (response != null) out.println(mapper.writeValueAsString(response));
      } catch (Exception e) {
        LOG.warning("Error handling request: " + e.getMessage());
        out.println(errorResponse(null, -32700, "Parse error: " + e.getMessage()));
      }
    }
  }

  JsonNode handle(JsonNode req) {
    JsonNode id     = req.has("id") ? req.get("id") : null;
    String   method = req.path("method").asText();
    JsonNode params = req.path("params");

    return switch (method) {
      case "initialize"    -> initializeResponse(id);
      case "tools/list"    -> toolsListResponse(id);
      case "tools/call"    -> toolsCallResponse(id, params);
      case "prompts/list"  -> promptsListResponse(id);
      case "prompts/get"   -> promptsGetResponse(id, params);
      // Notifications have no id and require no response — return null to skip output
      case "notifications/initialized", "notifications/cancelled" -> null;
      default -> {
        try { yield mapper.readTree(errorResponse(id, -32601, "Method not found: " + method)); }
        catch (Exception ex) { yield mapper.createObjectNode(); }
      }
    };
  }

  private JsonNode initializeResponse(JsonNode id) {
    ObjectNode result = mapper.createObjectNode();
    result.put("protocolVersion", "2024-11-05");
    result.putObject("capabilities").putObject("tools");
    result.with("capabilities").putObject("prompts");
    ObjectNode info = result.putObject("serverInfo");
    info.put("name", SERVER_NAME);
    info.put("version", SERVER_VERSION);
    return response(id, result);
  }

  private JsonNode toolsListResponse(JsonNode id) {
    ArrayNode tools = mapper.createArrayNode();
    tools.add(tool("testara_summary",    "Summarize feature files at scenario, feature, or directory level",
        requiredStr("path", "Path to .feature file or directory (absolute, or relative to projectRoot)"),
        optionalStr("scenario", "Filter to specific scenario name"),
        optionalStr("projectRoot", "Project root. Required when MCP server was launched outside the workspace.")));
    tools.add(tool("testara_overview",   "Statistical overview of the test project",
        optionalStr("path", "Project root (default: MCP server root)"),
        optionalStr("format", "Output format: markdown or json"),
        optionalStr("projectRoot", "Project root override. Required when MCP server was launched outside the workspace.")));
    tools.add(tool("testara_review",     "Review feature files for quality issues",
        requiredStr("path", "Path to .feature file or directory (absolute, or relative to projectRoot)"),
        optionalStr("projectRoot", "Project root. Required when MCP server was launched outside the workspace.")));
    tools.add(tool("testara_run",        "Resolve natural-language test intent to tag expression, optionally execute",
        requiredStr("input", "Natural-language test run request"),
        optionalBool("dryRun", "Show plan only — default true"),
        optionalBool("execute", "Actually execute Maven — default false"),
        optionalStr("module", "Restrict to Maven module"),
        optionalStr("projectRoot", "Project root. Required when MCP server was launched outside the workspace (e.g. from home dir).")));
    tools.add(tool("testara_command",    "List project commands, show command detail, or generate a new CommandLogic<T> class. Omit description to list all.",
        optionalStr("description", "Omit to list all commands; 'detail:<name>' for details; or describe a new command to generate"),
        optionalStr("detail", "Command name to show source and usage docs for"),
        optionalStr("package", "Target Java package"),
        optionalStr("returnType", "Return type")));
    tools.add(tool("testara_command_detail", "Show source, return type, aliases and how to use a specific Testara command",
        requiredStr("name", "Command name or alias to look up")));
    tools.add(tool("testara_validation", "List project validations, show validation detail, or generate a new ValidatorLogic class. Omit description to list all.",
        optionalStr("description", "Omit to list all validations; 'detail:<name>' for details; or describe a new validation to generate"),
        optionalStr("detail", "Validation name to show when-to-use and how-to-use docs for"),
        optionalStr("mode", "auto, json, or java"),
        optionalStr("package", "Target Java package")));
    tools.add(tool("testara_validation_detail", "Show source, types, aliases and when/how to use a specific Testara validation",
        requiredStr("name", "Validation name or alias to look up")));
    tools.add(tool("testara_plan",       "Generate a Testara-compatible Cucumber feature from intent",
        requiredStr("intent", "Test plan intent"),
        optionalStr("slice", "Layer: api, ui, database, streaming, fullstack"),
        optionalStr("domain", "Domain name override")));
    tools.add(tool("testara_guide",      "Return Testara agent guide and generation rules. Call at session start or before generating any artifact.",
        optionalStr("section", "Rule section to retrieve: properties | request-spec | ui | quirks | db | kafka | all (default). Use 'quirks' to get UI runtime quirks before generating UI features.")));
    tools.add(tool("testara_context",    "Return full Testara runtime context — slices installed, config coverage, available steps, commands, validations",
        optionalStr("projectRoot", "Project root. Required when MCP server was launched outside the workspace (e.g. from home dir).")));
    tools.add(tool("testara_property",   "Manage property keys — list, suggest key for a value, generate config block, or explain properties() rules",
        optionalStr("mode", "list | suggest | generate | rules"),
        optionalStr("domain", "Domain name for generated keys"),
        optionalStr("value", "Value to suggest a property key for"),
        optionalStr("slice", "Slice for config block generation: api, ui, sql, mongo, kafka")));
    tools.add(tool("testara_api",        "Explain API config, generate api.service block, or generate request spec JSON",
        optionalStr("mode", "explain | config | request-spec"),
        optionalStr("domain", "Service/domain name"),
        optionalStr("flow", "Request spec flow name"),
        optionalStr("method", "HTTP method: GET, POST, PUT, PATCH, DELETE"),
        optionalStr("endpoint", "Endpoint URL or path")));
    tools.add(tool("testara_ui",         "Generate Testara UI artifacts — Page class, UserAction class, engine config, or interaction catalog. Returns structured artifact with file_path and source for one-call file creation.",
        optionalStr("mode", "explain | page | action | config | interactions | validate-page. Use mode=interactions to get the full catalog of valid Click/Enter/SeeThat/WaitUntil/Navigate/observation classes before writing UserAction methods."),
        optionalStr("pageName", "Page name (e.g. login)"),
        optionalStr("actionName", "Action description (e.g. login with credential)"),
        optionalStr("engine", "UI engine: selenium | playwright | appium"),
        optionalStr("basePackage", "Base Java package"),
        optionalStr("htmlSnapshot", "Optional rendered HTML snapshot for mode=validate-page selector existence checks"),
        optionalStr("format", "concise (default for MCP) or markdown"),
        optionalStr("projectRoot", "Project root. Required when writing files or when MCP server was launched outside the workspace."),
        optionalBool("write", "Write the generated file to disk. Default false — returns structured artifact for manual creation.")));
    tools.add(tool("testara_bootstrap",  "Create or preview dynamic Testara bootstrap artifacts: UI page/action bundles, API specs/config, Command, and Validation skeletons.",
        optionalStr("artifact", "page | action | ui-bundle | request-spec | api-config | command | validation"),
        optionalStr("intent", "Natural-language artifact intent"),
        optionalStr("pageName", "UI page name"),
        optionalStr("actionName", "UI action name or command/validation description"),
        optionalStr("domain", "API/service domain"),
        optionalStr("flow", "API request spec flow"),
        optionalStr("method", "HTTP method"),
        optionalStr("endpoint", "API endpoint/path"),
        optionalStr("engine", "UI engine: selenium | playwright | appium"),
        optionalStr("basePackage", "Base Java package"),
        optionalStr("projectRoot", "Project root. Required when writing files or when MCP server was launched outside the workspace."),
        optionalBool("write", "Write generated artifact files to disk. Default false.")));
    tools.add(tool("testara_db",         "Explain and generate DB (SQL/Mongo/Elastic) or Kafka config and feature templates",
        optionalStr("slice", "sql | mongo | kafka | elastic"),
        optionalStr("mode", "explain | config | feature"),
        optionalStr("name", "Service name (e.g. settlementDb, paymentKafka, catalogElastic)")));
    tools.add(tool("testara_validate",   "Pre-flight validation: checks property file locations, glue config, TODO selectors, and required config keys before running tests",
        optionalStr("projectRoot", "Project root. Required when MCP server was launched outside the workspace.")));
    tools.add(tool("testara_debug",      "Diagnose failed Testara step/report snippets and suggest likely root cause",
        optionalStr("failedStep", "Exact failed Gherkin step, e.g. Then user should see \"error\" is displayed"),
        optionalStr("snippet", "Stack trace, report excerpt, or console failure snippet"),
        optionalStr("projectRoot", "Project root. Required when MCP server was launched outside the workspace.")));
    tools.add(tool("testara_init",       "Bootstrap or integrate a Testara automation project. For ui/fullstack types, OMIT the engine param so the skill prompts the user to choose — do NOT default to selenium.",
        optionalStr("projectRoot", "Target workspace root. Required for writes when MCP server was launched outside the workspace."),
        optionalStr("type", "api, ui, database, streaming, fullstack"),
        optionalStr("groupId", "Maven groupId. Defaults to io.github.ygrip when omitted."),
        optionalStr("artifactId", "Maven artifactId. Defaults to the project directory name when omitted."),
        optionalStr("basePackage", "Base Java package"),
        optionalStr("engine", "UI engine: selenium | playwright | appium. OMIT on first call for ui/fullstack — the skill will prompt the user to choose. Pass engine only after the user has explicitly answered."),
        optionalBool("autoGenerateCoordinates", "Use generated Maven coordinates when groupId/artifactId are omitted. Default false so agents ask first."),
        optionalBool("integrateExisting", "Integrate into existing project"),
        optionalBool("includeExamples", "Also generate demo sample feature/page/request artifacts. Default false; prefer contextual generation."),
        optionalBool("write", "Create files on disk. Default false."),
        optionalBool("compile", "Run test-compile after writing files. Default true.")));

    ObjectNode result = mapper.createObjectNode();
    result.set("tools", tools);
    return response(id, result);
  }

  private JsonNode toolsCallResponse(JsonNode id, JsonNode params) {
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
    if (args.path("write").asBoolean(false) && !writeEnabled()) {
      return writeDisabledMessage(name);
    }
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
      case "testara_command_detail" -> commandSkill.execute("detail:" + args.path("name").asText(""), ctx);
      case "testara_validation" -> validationSkill.execute(args.path("description").asText(""), ctx);
      case "testara_validation_detail" -> validationSkill.execute("detail:" + args.path("name").asText(""), ctx);
      case "testara_plan" -> planSkill.execute(new TestPlanSkill.Input(
          args.path("intent").asText(""),
          args.path("slice").asText(null),   // null → inferSlice() runs; "api" was masking UI/DB intents
          args.path("domain").asText(null),
          List.of()), ctx);
      case "testara_init" -> initSkill.execute(new TestInitSkill.Input(
          args.path("type").asText("api"),
          args.path("basePackage").asText(null),
          args.path("engine").asText(null),   // null → engine prompt fires for ui/fullstack types
          args.path("integrateExisting").asBoolean(false),
          args.path("groupId").asText(null),
          args.path("artifactId").asText(null)), ctx);
      case "testara_guide"    -> guideSkill.execute(args.path("section").asText(null), ctx);
      case "testara_context"  -> contextSkill.execute(null, ctx);
      case "testara_property" -> propertySkill.execute(new TestaraPropertySkill.Input(
          args.path("mode").asText("list"), args.path("domain").asText(null),
          args.path("value").asText(null), args.path("slice").asText(null)), ctx);
      case "testara_api"      -> apiSkill.execute(new TestaraApiSkill.Input(
          args.path("mode").asText("explain"), args.path("domain").asText(null),
          args.path("flow").asText(null), args.path("method").asText(null),
          args.path("endpoint").asText(null)), ctx);
      case "testara_ui"       -> uiSkill.execute(new TestaraUiSkill.Input(
          args.path("mode").asText("explain"), args.path("pageName").asText(null),
          args.path("actionName").asText(null), args.path("engine").asText(null),
          args.path("basePackage").asText(null), args.path("htmlSnapshot").asText(null)), ctx);
      case "testara_bootstrap" -> bootstrapSkill.execute(new TestaraBootstrapSkill.Input(
          args.path("artifact").asText("ui"), args.path("intent").asText(null),
          args.path("pageName").asText(null), args.path("actionName").asText(null),
          args.path("domain").asText(null), args.path("flow").asText(null),
          args.path("method").asText(null), args.path("endpoint").asText(null),
          args.path("basePackage").asText(null), args.path("engine").asText(null)), ctx);
      case "testara_db"       -> dbSkill.execute(new TestaraDbSkill.Input(
          args.path("slice").asText("sql"), args.path("mode").asText("explain"),
          args.path("name").asText(null)), ctx);
      case "testara_validate" -> validateSkill.execute(null, ctx);
      case "testara_debug"    -> debugSkill.execute(new TestaraDebugSkill.Input(
          args.path("snippet").asText(null), args.path("failedStep").asText(null)), ctx);
      default -> throw new IllegalArgumentException("Unknown tool: " + name);
    };
  }

  private boolean writeEnabled() {
    // Write is enabled by default — set TESTARA_AGENT_WRITE_ENABLED=false to disable
    return !"false".equalsIgnoreCase(System.getenv().getOrDefault("TESTARA_AGENT_WRITE_ENABLED", "true"));
  }

  private String writeDisabledMessage(String toolName) {
    return """
        write_disabled: TESTARA_AGENT_WRITE_ENABLED=false is set in the environment
        capability_available: %s can preview artifacts — call with write=false to get file_path/source
        next_step: remove TESTARA_AGENT_WRITE_ENABLED=false from env to re-enable writes
        """.formatted(toolName);
  }

  private AgentContext buildContext(String toolName, JsonNode args) {
    Map<String, String> opts = new LinkedHashMap<>();
    Path effectiveRoot = resolveProjectRoot(args);
    // Secure defaults: no execution, no writes
    opts.put("dryRun", args.path("dryRun").asBoolean(true) ? "true" : "false");
    opts.put("execute", args.path("execute").asBoolean(false) ? "true" : "false");
    // Default to concise for MCP — agents don't need decorative markdown
    opts.put("format", args.has("format") ? args.path("format").asText() : "concise");
    if (args.has("mode"))   opts.put("mode",   args.path("mode").asText("auto"));
    if (args.has("package")) opts.put("package", args.path("package").asText());
    if (args.has("returnType")) opts.put("returnType", args.path("returnType").asText("String"));
    if (args.has("module"))  opts.put("module", args.path("module").asText());
    if (args.has("detail"))  opts.put("detail", args.path("detail").asText());
    if (args.has("write"))   opts.put("write", args.path("write").asBoolean(false) ? "true" : "false");
    if (args.has("compile")) opts.put("compile", args.path("compile").asBoolean(true) ? "true" : "false");
    if (args.has("autoGenerateCoordinates")) {
      opts.put("autoGenerateCoordinates", args.path("autoGenerateCoordinates").asBoolean(false) ? "true" : "false");
    }
    if (args.has("includeExamples")) {
      opts.put("includeExamples", args.path("includeExamples").asBoolean(false) ? "true" : "false");
    }
    if (args.has("projectRoot")) opts.put("projectRootExplicit", "true");
    // engine is provided → user confirmed the choice, no engine prompt needed
    // engineConfirmed is kept for backward compat but no longer used — prompt only fires when engine=null

    AgentMode mode = toolName.equals("testara_run") ? AgentMode.PLAN : AgentMode.READ_ONLY;

    LlmConfig cfg = LlmConfig.fromEnv();
    var llm = cfg.hasApiKey() ? new OpenAiLlmClient(cfg) : (io.github.ygrip.testara.agent.llm.LlmClient) new DisabledLlmClient();
    return new AgentContext(effectiveRoot, refreshedProfile(effectiveRoot), mode, llm, opts);
  }

  private Path resolveProjectRoot(JsonNode args) {
    if (!args.has("projectRoot") || args.path("projectRoot").asText().isBlank()) return projectRoot;
    Path requested = Paths.get(args.path("projectRoot").asText());
    if (!requested.isAbsolute()) requested = projectRoot.resolve(requested);
    return requested.toAbsolutePath().normalize();
  }

  private TestaraProjectProfile refreshedProfile(Path root) {
    if (!Files.exists(root.resolve("pom.xml")) && !Files.exists(root.resolve("build.gradle"))) {
      return profile;
    }
    try {
      TestaraProjectProfile refreshed = JsonlKnowledgeStore.loadProfile(root);
      if (root.equals(projectRoot)) profile = refreshed;
      return refreshed;
    } catch (Exception e) {
      LOG.fine("Cannot refresh project index: " + e.getMessage());
    }
    return profile;
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

  private ObjectNode response(JsonNode id, JsonNode result) {
    ObjectNode r = mapper.createObjectNode();
    r.put("jsonrpc", "2.0");
    if (id != null) r.set("id", id);
    r.set("result", result);
    return r;
  }

  private String errorResponse(JsonNode id, int code, String message) {
    try {
      ObjectNode r = mapper.createObjectNode();
      r.put("jsonrpc", "2.0");
      if (id != null) r.set("id", id);
      ObjectNode err = r.putObject("error");
      err.put("code", code);
      err.put("message", message);
      return mapper.writeValueAsString(r);
    } catch (Exception e) {
      return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Internal error\"}}";
    }
  }

  // ── Prompts ───────────────────────────────────────────────────────

  private JsonNode promptsListResponse(JsonNode id) {
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

  private JsonNode promptsGetResponse(JsonNode id, JsonNode params) {
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
