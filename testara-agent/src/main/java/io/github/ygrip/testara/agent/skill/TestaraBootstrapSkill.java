package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Skill: create preview/write bootstrap artifacts using framework-correct skeletons.
 */
public class TestaraBootstrapSkill implements AgentSkill<TestaraBootstrapSkill.Input, String> {

  private final TestaraUiSkill uiSkill = new TestaraUiSkill();
  private final TestaraApiSkill apiSkill = new TestaraApiSkill();

  public record Input(String artifact, String intent, String pageName, String actionName,
      String domain, String flow, String method, String endpoint, String basePackage, String engine) {}

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
        return "artifact: " + artifact + "\nwritten: " + relativePath + "\nscan-location: " + scanHint;
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
}
