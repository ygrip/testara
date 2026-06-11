package io.github.ygrip.testara.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Skill: create preview/write bootstrap artifacts using framework-correct skeletons.
 */
public class TestaraBootstrapSkill implements AgentSkill<TestaraBootstrapSkill.Input, String> {

  private final TestaraUiSkill uiSkill = new TestaraUiSkill();
  private final TestaraApiSkill apiSkill = new TestaraApiSkill();
  private final ObjectMapper mapper = new ObjectMapper();
  private final TestPlanSkill planSkill = new TestPlanSkill();

  public record Input(String artifact, String intent, String pageName, String actionName,
      String domain, String flow, String method, String endpoint, String basePackage, String engine,
      String mode, String pages, String actions, String baseUrl,
      boolean generateFeatures, String featureFiles, boolean createFiles) {
    public Input(String artifact, String intent, String pageName, String actionName,
        String domain, String flow, String method, String endpoint, String basePackage, String engine) {
      this(artifact, intent, pageName, actionName, domain, flow, method, endpoint, basePackage, engine,
          null, null, null, null, false, null, false);
    }
  }

  @Override
  public String name() { return "testara-bootstrap"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String artifact = normalize(input.artifact(), "ui");
    boolean write = "true".equals(context.options().get("write"));
    boolean concise = "concise".equals(context.options().get("format"));
    String basePackage = input.basePackage() == null || input.basePackage().isBlank()
        ? context.options().getOrDefault("package", "io.github.ygrip.automation")
        : input.basePackage();

    if ("batch".equals(artifact) || "ui-batch".equals(artifact) || "batch".equals(normalize(input.mode(), ""))) {
      return uiBatch(input, context, basePackage);
    }

    return switch (artifact) {
      case "page" -> uiSkill.execute(new TestaraUiSkill.Input("page",
          first(input.pageName(), inferPage(input.intent())), null, input.engine(), basePackage), context);
      case "action" -> uiSkill.execute(new TestaraUiSkill.Input("action",
          first(input.pageName(), inferPage(input.intent())),
          first(input.actionName(), input.intent(), "perform action"), input.engine(), basePackage), context);
      case "ui", "ui-bundle", "page-action" -> uiBundle(input, context, basePackage);
      case "api-config" -> apiSkill.execute(new TestaraApiSkill.Input("config",
          first(input.domain(), inferDomain(input.intent())), null, null, null), context);
      case "request-spec", "api-request-spec" -> apiSkill.execute(new TestaraApiSkill.Input("request-spec",
          first(input.domain(), inferDomain(input.intent())),
          first(input.flow(), input.intent(), "request"),
          first(input.method(), "GET"), first(input.endpoint(), "/")), context);
      case "command" -> command(input, context, basePackage, write, concise);
      case "validation", "validator" -> validation(input, context, basePackage, write, concise);
      default -> needsInput(artifact);
    };
  }

  private String uiBundle(Input input, AgentContext context, String basePackage) {
    String pageName = first(input.pageName(), inferPage(input.intent()));
    String actionName = first(input.actionName(), input.intent(), "perform action");
    String page = uiSkill.execute(new TestaraUiSkill.Input("page", pageName, null, input.engine(), basePackage), context);
    String action = uiSkill.execute(new TestaraUiSkill.Input("action", pageName, actionName, input.engine(), basePackage), context);
    return "artifact: ui-bundle\n\n" + page + "\n\n" + action;
  }

  private String uiBatch(Input input, AgentContext context, String basePackage) {
    List<PageSpec> pages = parsePages(input);
    if (pages.isEmpty()) {
      String inferredPage = first(input.pageName(), inferPage(input.intent()));
      pages = List.of(new PageSpec(inferredPage, actionsFor(input.actionName(), input.actions())));
    }

    List<String> createdFiles = new ArrayList<>();
    List<String> actionCatalog = new ArrayList<>();
    List<String> pageCatalog = new ArrayList<>();
    List<String> locatorCatalog = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> filesChanged = new ArrayList<>();
    StringBuilder raw = new StringBuilder();

    for (PageSpec pageSpec : pages) {
      String pageName = first(pageSpec.name(), "page");
      String pageKey = toKebab(pageName);
      String pageClass = toClassName(pageKey) + "Page";
      pageCatalog.add(pageKey + " -> " + pageClass);
      String pageOutput = uiSkill.execute(new TestaraUiSkill.Input("page", pageName, null, input.engine(), basePackage,
          null), context);
      raw.append("\n--- page ").append(pageKey).append(" ---\n").append(pageOutput).append("\n");
      String pagePath = "src/main/java/" + basePackage.replace('.', '/') + "/page/" + pageClass + ".java";
      createdFiles.add(pagePath);
      filesChanged.add("created " + pagePath + " [class:" + pageClass + "]");
      locatorCatalog.add(pageKey + ": see generated " + pageClass + " locator fields");
      if (pageOutput.contains("TODO")) warnings.add(pageKey + " has low-confidence TODO locators");

      List<String> actionNames = pageSpec.actions().stream()
          .filter(action -> action != null && !action.isBlank())
          .toList();
      if (!actionNames.isEmpty()) {
        String actionClass = toClassName(pageKey) + "Actions";
        String actionPath = "src/main/java/" + basePackage.replace('.', '/') + "/action/" + actionClass + ".java";
        createdFiles.add(actionPath);
        ActionWrite actionWrite = writeBatchActions(pageName, pageClass, actionClass, actionNames,
            basePackage, context.projectRoot(), "true".equals(context.options().get("write")));
        raw.append("\n--- actions ").append(pageKey).append(" ---\n").append(actionWrite.summary()).append("\n");
        actionCatalog.addAll(actionWrite.catalog());
        String actionSymbols = actionWrite.catalog().isEmpty() ? "" : "; actions:" + String.join(",", actionWrite.catalog());
        filesChanged.add("created " + actionPath + " [class:" + actionClass + actionSymbols + "]");
      }
    }

    String recommendedRun = "@regression";
    StringBuilder sb = new StringBuilder();
    sb.append("artifact: ui-batch\n");
    sb.append("mode: batch\n");
    sb.append("pages: ").append(pages.size()).append("\n");
    sb.append("actions: ").append(actionCatalog.size()).append("\n");
    sb.append("createdFiles:\n");
    createdFiles.stream().distinct().forEach(path -> sb.append("- ").append(path).append("\n"));
    sb.append("pageCatalog:\n");
    pageCatalog.forEach(page -> sb.append("- ").append(page).append("\n"));
    sb.append("actionCatalog:\n");
    actionCatalog.forEach(action -> sb.append("- ").append(action).append("\n"));
    sb.append("locatorCatalog:\n");
    locatorCatalog.forEach(locator -> sb.append("- ").append(locator).append("\n"));
    sb.append("warnings:\n");
    if (warnings.isEmpty()) sb.append("- none\n");
    else warnings.forEach(w -> sb.append("- ").append(w).append("\n"));
    sb.append("filesChanged:\n");
    if (filesChanged.isEmpty()) sb.append("- none\n");
    else filesChanged.forEach(entry -> sb.append("- ").append(entry).append("\n"));
    sb.append("nextRecommendedCommand: testara_run --tags ").append(recommendedRun).append("\n");
    if (input.generateFeatures()) {
      String featureFiles = first(input.featureFiles(), defaultFeatureFiles(pages));
      String planOutput = planSkill.execute(new TestPlanSkill.Input(first(input.intent(), "generated ui flow"),
          "ui", first(input.domain(), "ui"), List.of(), "batch", featureFiles,
          input.createFiles() || "true".equals(context.options().get("write")), true), context);
      sb.append("featureGeneration:\n").append(indent(planOutput)).append("\n");
    }
    if (!"summary".equals(context.options().get("format"))) {
      sb.append("\nrawArtifacts:\n").append(raw);
    }
    return sb.toString();
  }

  private String command(Input input, AgentContext context, String basePackage, boolean write, boolean concise) {
    String description = first(input.intent(), input.actionName(), "custom command");
    String commandName = toKebab(description);
    String className = toClassName(commandName) + "Command";
    String pkg = basePackage + ".command";
    String relativePath = "src/main/java/" + pkg.replace('.', '/') + "/" + className + ".java";
    String source = """
        package %s;

        import io.github.ygrip.testara.command.model.CommandLogic;
        import io.github.ygrip.testara.command.model.CommandTag;

        import java.util.List;

        @CommandTag(command = "%s")
        public class %s implements CommandLogic<String> {

          @Override
          public boolean preProcessParameters() {
            return false;
          }

          @Override
          public String execute(List<Object> parameters) throws Exception {
            // TODO: implement command logic.
            // Use parameters.get(0) for the first argument when needed.
            return "";
          }
        }
        """.formatted(pkg, commandName, className);
    return renderArtifact("command", relativePath, source,
        "command.executor.scan-locations=io.github.ygrip.testara," + pkg, context.projectRoot(), write, concise);
  }

  private String validation(Input input, AgentContext context, String basePackage, boolean write, boolean concise) {
    String description = first(input.intent(), input.actionName(), "custom validation");
    String validationName = toKebab(description);
    String className = toClassName(validationName) + "Validator";
    String pkg = basePackage + ".validation";
    String relativePath = "src/main/java/" + pkg.replace('.', '/') + "/" + className + ".java";
    String source = """
        package %s;

        import io.github.ygrip.testara.validation.model.ValidationTag;
        import io.github.ygrip.testara.validation.model.ValidatorLogic;

        @ValidationTag(command = "%s")
        public class %s extends ValidatorLogic<Object, Object> {

          @Override
          protected String setDefaultMessage() {
            return "%s validation failed";
          }

          @Override
          public boolean validate() throws Exception {
            // TODO: implement validation logic using getActual() and getExpected().
            return false;
          }
        }
        """.formatted(pkg, validationName, className, validationName);
    return renderArtifact("validation", relativePath, source,
        "validator.helper.scan-locations=io.github.ygrip.testara," + pkg, context.projectRoot(), write, concise);
  }

  private String renderArtifact(String artifact, String relativePath, String source, String scanHint,
      Path root, boolean write, boolean concise) {
    if (write) {
      try {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source, StandardCharsets.UTF_8);
        String className = target.getFileName().toString().replace(".java", "");
        return "artifact: " + artifact + "\nwritten: " + relativePath
            + "\nfilesChanged:\n- created " + relativePath + " [class:" + className + "; type:" + artifact + "]"
            + "\nscan-location: " + scanHint;
      } catch (IOException e) {
        return "Error: " + e.getMessage();
      }
    }
    if (concise) {
      return "artifact: " + artifact + "\nfile_path: " + relativePath + "\nscan-location: " + scanHint
          + "\n```java\n" + source.strip() + "\n```";
    }
    return "## Bootstrap " + artifact + "\n\n**Path:** `" + relativePath + "`\n\n```java\n"
        + source + "```\n\n**Scan location:**\n```properties\n" + scanHint + "\n```\n";
  }

  private String needsInput(String artifact) {
    return "needs_input: testara_bootstrap_artifact\n"
        + "question: Which artifact should be generated? Choose page, action, ui-bundle, request-spec, api-config, command, or validation.\n"
        + "input-artifact: " + artifact;
  }

  private String inferPage(String intent) {
    String lower = normalize(intent, "");
    if (lower.contains("login")) return "login";
    if (lower.contains("checkout")) return "checkout";
    if (lower.contains("cart")) return "cart";
    return "page";
  }

  private String inferDomain(String intent) {
    String lower = normalize(intent, "");
    if (lower.contains("payment")) return "payment";
    if (lower.contains("order")) return "order";
    if (lower.contains("user")) return "user";
    return "default";
  }

  private String first(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.toLowerCase(Locale.ROOT);
  }

  private String toKebab(String value) {
    String slug = value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
    return slug.isBlank() ? "custom-artifact" : slug.substring(0, Math.min(50, slug.length()));
  }

  private String toClassName(String value) {
    StringBuilder sb = new StringBuilder();
    for (String part : value.split("[-_]")) {
      if (!part.isBlank()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    return sb.isEmpty() ? "CustomArtifact" : sb.toString();
  }

  private List<PageSpec> parsePages(Input input) {
    String raw = input.pages();
    if (raw == null || raw.isBlank()) return List.of();
    try {
      JsonNode node = mapper.readTree(raw);
      if (!node.isArray()) return List.of();
      List<PageSpec> specs = new ArrayList<>();
      for (JsonNode page : node) {
        String name = page.path("name").asText(page.path("pageName").asText(""));
        Set<String> actions = new LinkedHashSet<>();
        if (page.has("actions") && page.path("actions").isArray()) {
          page.path("actions").forEach(a -> {
            if (a.isTextual()) actions.add(a.asText());
            else actions.add(a.path("name").asText(a.path("actionName").asText("")));
          });
        }
        specs.add(new PageSpec(name, actions.stream().filter(a -> !a.isBlank()).toList()));
      }
      return specs;
    } catch (IOException e) {
      return List.of(new PageSpec(raw.split(",")[0].strip(), actionsFor(input.actionName(), input.actions())));
    }
  }

  private String defaultFeatureFiles(List<PageSpec> pages) {
    var root = mapper.createArrayNode();
    for (PageSpec page : pages) {
      var feature = root.addObject();
      String pageKey = toKebab(first(page.name(), "page"));
      feature.put("path", "src/test/resources/features/ui/" + pageKey + ".feature");
      feature.put("featureName", toClassName(pageKey));
      var tags = feature.putArray("tags");
      tags.add("@ui");
      tags.add("@regression");
      tags.add("@" + pageKey);
      var scenarios = feature.putArray("scenarios");
      for (String action : page.actions()) {
        if (action == null || action.isBlank()) continue;
        var scenario = scenarios.addObject();
        scenario.put("name", toClassName(action) + " succeeds");
        scenario.put("intent", action + " on " + pageKey + " page");
        var scenarioTags = scenario.putArray("tags");
        scenarioTags.add("@positive");
      }
    }
    return root.toString();
  }

  private String indent(String text) {
    return text.lines().map(line -> "  " + line).reduce("", (left, right) -> left + right + "\n").stripTrailing();
  }

  private List<String> actionsFor(String actionName, String rawActions) {
    Set<String> actions = new LinkedHashSet<>();
    if (actionName != null && !actionName.isBlank()) actions.add(actionName);
    if (rawActions != null && !rawActions.isBlank()) {
      try {
        JsonNode node = mapper.readTree(rawActions);
        if (node.isArray()) node.forEach(a -> actions.add(a.isTextual() ? a.asText() : a.path("name").asText("")));
      } catch (IOException e) {
        for (String part : rawActions.split(",")) if (!part.isBlank()) actions.add(part.strip());
      }
    }
    if (actions.isEmpty()) actions.add("perform action");
    return actions.stream().filter(a -> !a.isBlank()).toList();
  }

  private ActionWrite writeBatchActions(String pageName, String pageClass, String actionClass,
      List<String> actionNames, String basePackage, Path root, boolean write) {
    StringBuilder methods = new StringBuilder();
    List<String> catalog = new ArrayList<>();
    for (String actionName : actionNames) {
      String normalizedAction = semanticActionName(actionName);
      String methodName = toCamelCase(normalizedAction);
      catalog.add(normalizedAction + " -> " + methodName);
      methods.append("""

          @Action("%s")
          public void %s(Map<String, Object> params) {
            attemptsTo(
        %s
            );
          }
      """.formatted(normalizedAction, methodName, interactionsFor(normalizedAction, pageName)));
    }
    String source = """
        package %s.action;

        import %s.page.%s;
        import io.github.ygrip.testara.ui.executor.UserAction;
        import io.github.ygrip.testara.ui.interaction.Click;
        import io.github.ygrip.testara.ui.interaction.Enter;
        import io.github.ygrip.testara.ui.model.Action;
        import io.github.ygrip.testara.ui.model.OnPage;

        import java.util.Map;

        @OnPage(value = {%s.class})
        public class %s extends UserAction {
        %s
        }
        """.formatted(basePackage, basePackage, pageClass, pageClass, actionClass, methods);
    String relativePath = "src/main/java/" + basePackage.replace('.', '/') + "/action/" + actionClass + ".java";
    if (!write) return new ActionWrite("file_path: " + relativePath + "\n```java\n" + source.strip() + "\n```", catalog);
    try {
      Path target = root.resolve(relativePath);
      Files.createDirectories(target.getParent());
      Files.writeString(target, source, StandardCharsets.UTF_8);
      return new ActionWrite("written: " + relativePath + "\nactions: " + catalog.size(), catalog);
    } catch (IOException e) {
      return new ActionWrite("Error: " + e.getMessage(), catalog);
    }
  }

  private String interactionsFor(String actionName, String pageName) {
    String lower = actionName.toLowerCase(Locale.ROOT);
    if (lower.contains("login") || lower.contains("credential")) {
      return """
              Enter.text(String.valueOf(params.get("username"))).into("username field"),
              Enter.text(String.valueOf(params.get("password"))).into("password field"),
              Click.on("button login")
          """.stripTrailing();
    }
    if (lower.contains("search")) {
      return """
              Enter.text(String.valueOf(params.get("query"))).into("search input"),
              Click.on("button search")
          """.stripTrailing();
    }
    if (lower.contains("cart")) {
      return "        Click.on(\"button cart\")";
    }
    return "        Click.on(\"primary action\")";
  }

  private String semanticActionName(String actionName) {
    String semantic = actionName.toLowerCase(Locale.ROOT)
        .replaceAll("https?://\\S+", " ")
        .replaceAll("\\b(password|username|token|secret)\\s+\\S+", " ")
        .replaceAll("[^a-z0-9]+", " ")
        .replaceAll("\\s+", " ")
        .strip();
    return semantic.isBlank() ? "perform action" : semantic;
  }

  private String toCamelCase(String value) {
    String[] parts = value.replaceAll("[^a-zA-Z0-9]+", " ").trim().split("\\s+");
    if (parts.length == 0 || parts[0].isBlank()) return "performAction";
    StringBuilder sb = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].isBlank()) {
        sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase(Locale.ROOT));
      }
    }
    String method = sb.toString();
    return method.length() <= 60 ? method : method.substring(0, 60);
  }

  private record PageSpec(String name, List<String> actions) {}
  private record ActionWrite(String summary, List<String> catalog) {}
}
