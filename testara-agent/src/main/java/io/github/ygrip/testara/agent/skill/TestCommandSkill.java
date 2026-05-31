package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.CommandIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Skill: list commands, show command detail, or generate a new CommandLogic<T> class.
 *
 * Modes:  blank/"--list" → list | "detail:<name>" → detail | description → generate
 * Format: "concise" in options for token-efficient output (default for MCP)
 */
public class TestCommandSkill implements AgentSkill<String, String> {

  @Override
  public String name() { return "test-command"; }

  @Override
  public String execute(String description, AgentContext context) {
    List<CommandIndex> commands = context.profile().commands();
    boolean concise = "concise".equals(context.options().get("format"));

    String detail = context.options().get("detail");
    if (description == null || description.isBlank() || "--list".equalsIgnoreCase(description.strip())) {
      return new ListCommandsSkill().execute(null, context);
    }

    String detailName = detail != null ? detail
        : (description.startsWith("detail:") ? description.substring(7).strip() : null);
    if (detailName != null) {
      return renderDetail(detailName, commands, concise);
    }

    return generateCommand(description, commands, context, concise);
  }

  // ── Detail ───────────────────────────────────────────────────────────────

  private String renderDetail(String name, List<CommandIndex> commands, boolean concise) {
    Optional<CommandIndex> found = commands.stream()
        .filter(c -> c.command().equalsIgnoreCase(name)
            || c.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(name))
            || c.className().equalsIgnoreCase(name))
        .findFirst();

    if (found.isEmpty()) {
      String available = commands.isEmpty() ? "none"
          : commands.stream().map(CommandIndex::command).collect(Collectors.joining(", "));
      return "command '" + name + "' not found. available: " + available;
    }

    CommandIndex c = found.get();
    if (concise) {
      StringBuilder sb = new StringBuilder();
      sb.append("command: ").append(c.command());
      sb.append(" | class: ").append(c.className());
      sb.append(" | returns: ").append(c.returnType());
      sb.append(" | cacheable: ").append(c.cacheable() ? "yes" : "no");
      if (!c.aliases().isEmpty()) sb.append(" | aliases: ").append(String.join(", ", c.aliases()));
      sb.append("\nusage: ${").append(c.command()).append("(params)}");
      sb.append("\nsource: ").append(c.sourcePath());
      return sb.toString();
    }

    StringBuilder sb = new StringBuilder();
    sb.append("## Command: `").append(c.command()).append("`\n\n");
    sb.append("| Field | Value |\n|-------|-------|\n");
    sb.append("| **Class** | `").append(c.className()).append("` |\n");
    sb.append("| **Return type** | `").append(c.returnType()).append("` |\n");
    sb.append("| **Aliases** | ").append(c.aliases().isEmpty() ? "none"
        : c.aliases().stream().map(a -> "`" + a + "`").collect(Collectors.joining(", "))).append(" |\n");
    sb.append("| **Cacheable** | ").append(c.cacheable() ? "yes" : "no").append(" |\n");
    sb.append("| **Source** | `").append(c.sourcePath()).append("` |\n\n");
    sb.append("### How to use\n\n```\n${").append(c.command()).append("(param1, param2)}\n```\n\n");
    sb.append("### Source\n\n```java\n");
    try {
      sb.append(Files.readString(c.sourcePath(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      sb.append("// Source not readable: ").append(e.getMessage());
    }
    sb.append("```\n");
    return sb.toString();
  }

  // ── Generate ──────────────────────────────────────────────────────────────

  private String generateCommand(String description, List<CommandIndex> commands,
      AgentContext context, boolean concise) {
    String commandName = toCommandName(description);
    String className   = toClassName(commandName) + "Command";
    String basePackage = context.options().getOrDefault("package", "io.github.ygrip.testara.command");
    String returnType  = context.options().getOrDefault("returnType", "String");

    StringBuilder sb = new StringBuilder();

    commands.stream()
        .filter(c -> c.command().equals(commandName) || c.aliases().contains(commandName)
            || c.command().contains(commandName) || commandName.contains(c.command()))
        .findFirst()
        .ifPresent(c -> sb.append(concise
            ? "note: '" + c.command() + "' already exists (" + c.className() + "). run detail:" + c.command() + " to review.\n\n"
            : "> **Note:** Command `" + c.command() + "` already exists (`" + c.className()
              + "`). Consider reusing it or run `detail:" + c.command() + "` to see its signature.\n\n"));

    if (concise) {
      sb.append("command: ").append(commandName).append(" | class: ").append(className)
          .append(" | package: ").append(basePackage).append(" | returns: ").append(returnType).append("\n\n");
    } else {
      sb.append("## Generated Command: `").append(commandName).append("`\n\n");
    }

    sb.append(concise ? "```java\n" : "### " + className + ".java\n\n```java\n");
    sb.append(generateCommandClass(className, commandName, description, basePackage, returnType));
    sb.append("```\n\n");

    if (!concise) {
      sb.append("### Placement\n\n```\nsrc/test/java/").append(basePackage.replace('.', '/'))
          .append("/").append(className).append(".java\n```\n\n");
      sb.append("### Scan config\n\n```properties\ncommand.executor.scan-locations=io.github.ygrip.testara,")
          .append(basePackage).append("\n```\n");
    } else {
      sb.append("placement: src/test/java/").append(basePackage.replace('.', '/')).append("/").append(className).append(".java\n");
      sb.append("scan-locations: io.github.ygrip.testara,").append(basePackage).append("\n");
    }
    return sb.toString();
  }

  private String generateCommandClass(String className, String commandName, String description,
      String pkg, String returnType) {
    return """
        package %s;

        import io.github.ygrip.testara.command.model.CommandLogic;
        import io.github.ygrip.testara.command.model.CommandTag;

        import java.util.List;

        /**
         * %s
         */
        @CommandTag(command = "%s")
        public class %s implements CommandLogic<%s> {

          @Override
          public boolean preProcessParameters() { return false; }

          @Override
          public %s execute(List<Object> parameters) throws Exception {
            // TODO: implement — parameters.get(0) is the first argument
            throw new UnsupportedOperationException("Not yet implemented");
          }
        }
        """.formatted(pkg, description, commandName, className, returnType, returnType);
  }

  private String toCommandName(String description) {
    String slug = description.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return slug.substring(0, Math.min(40, slug.length()));
  }

  private String toClassName(String commandName) {
    StringBuilder sb = new StringBuilder();
    for (String p : commandName.split("[-_]"))
      if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
    return sb.toString();
  }
}
