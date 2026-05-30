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
 * Modes (determined by description):
 *   blank / "--list"          → list all indexed commands
 *   "detail:<name>"           → show source + usage docs for that command
 *   any other text            → generate a new command class (warns if similar exists)
 */
public class TestCommandSkill implements AgentSkill<String, String> {

  @Override
  public String name() { return "test-command"; }

  @Override
  public String execute(String description, AgentContext context) {
    List<CommandIndex> commands = context.profile().commands();

    // List mode — delegate to ListCommandsSkill for consistent output
    String detail = context.options().get("detail");
    if (description == null || description.isBlank() || "--list".equalsIgnoreCase(description.strip())) {
      return renderListInContext(context);
    }

    // Detail mode via option or "detail:<name>" prefix
    String detailName = detail != null ? detail
        : (description.startsWith("detail:") ? description.substring(7).strip() : null);
    if (detailName != null) {
      return renderDetail(detailName, commands);
    }

    // Generate mode
    return generateCommand(description, commands, context);
  }

  // ── List — delegates to ListCommandsSkill for consistent output ──────────

  private String renderListInContext(AgentContext context) {
    return new ListCommandsSkill().execute(null, context);
  }

  // ── Detail ─────────────────────────────────────────────────────────────────

  private String renderDetail(String name, List<CommandIndex> commands) {
    Optional<CommandIndex> found = commands.stream()
        .filter(c -> c.command().equalsIgnoreCase(name)
            || c.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(name))
            || c.className().equalsIgnoreCase(name))
        .findFirst();

    if (found.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("Command `").append(name).append("` not found in project index.\n\n");
      if (!commands.isEmpty()) {
        sb.append("**Available commands:** ");
        sb.append(commands.stream().map(c -> "`" + c.command() + "`").collect(Collectors.joining(", ")));
        sb.append("\n");
      }
      return sb.toString();
    }

    CommandIndex c = found.get();
    StringBuilder sb = new StringBuilder();
    sb.append("## Command: `").append(c.command()).append("`\n\n");
    sb.append("| Field | Value |\n|-------|-------|\n");
    sb.append("| **Class** | `").append(c.className()).append("` |\n");
    sb.append("| **Return type** | `").append(c.returnType()).append("` |\n");
    sb.append("| **Aliases** | ").append(c.aliases().isEmpty() ? "none"
        : c.aliases().stream().map(a -> "`" + a + "`").collect(Collectors.joining(", "))).append(" |\n");
    sb.append("| **Cacheable** | ").append(c.cacheable() ? "yes — result is cached per parameters" : "no").append(" |\n");
    sb.append("| **Source** | `").append(c.sourcePath()).append("` |\n\n");

    sb.append("### How to use\n\n");
    sb.append("Reference this command inside Testara step expressions or validation JSON:\n\n");
    sb.append("```\n${").append(c.command()).append("(param1, param2)}\n```\n\n");
    if (!c.aliases().isEmpty()) {
      sb.append("Aliases are interchangeable:\n```\n");
      c.aliases().forEach(a -> sb.append("${").append(a).append("(param1)}\n"));
      sb.append("```\n\n");
    }

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

  private String generateCommand(String description, List<CommandIndex> commands, AgentContext context) {
    String commandName = toCommandName(description);
    String className   = toClassName(commandName) + "Command";
    String basePackage = context.options().getOrDefault("package", "com.company.automation.commands");
    String returnType  = context.options().getOrDefault("returnType", "String");

    StringBuilder sb = new StringBuilder();

    // Warn if similar already exists
    commands.stream()
        .filter(c -> c.command().equals(commandName) || c.aliases().contains(commandName)
            || c.command().contains(commandName) || commandName.contains(c.command()))
        .findFirst()
        .ifPresent(c -> sb.append("> **Note:** Command `").append(c.command())
            .append("` already exists (`").append(c.className())
            .append("`). Consider reusing it or run `detail:").append(c.command())
            .append("` to see its signature.\n\n"));

    sb.append("## Generated Command: `").append(commandName).append("`\n\n");
    sb.append("### ").append(className).append(".java\n\n```java\n");
    sb.append(generateCommandClass(className, commandName, description, basePackage, returnType));
    sb.append("```\n\n");
    sb.append("### ").append(className).append("Test.java\n\n```java\n");
    sb.append(generateCommandTest(className, commandName, basePackage, returnType));
    sb.append("```\n\n");
    sb.append("### Placement\n\n");
    sb.append("```\nsrc/test/java/").append(basePackage.replace('.', '/')).append("/")
        .append(className).append(".java\n```\n\n");
    sb.append("### Scan Location Config\n\n");
    sb.append("```properties\ncommand.executor.scan-locations=io.github.ygrip.testara,")
        .append(basePackage).append("\n```\n");
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
         * Generated by Testara Agent — review before committing.
         */
        @CommandTag(command = "%s")
        public class %s implements CommandLogic<%s> {

          @Override
          public boolean preProcessParameters() {
            return false;
          }

          @Override
          public %s execute(List<Object> parameters) throws Exception {
            // TODO: implement command logic
            // parameters.get(0) = first argument passed in the command expression
            throw new UnsupportedOperationException("Not yet implemented");
          }
        }
        """.formatted(pkg, description, commandName, className, returnType, returnType);
  }

  private String generateCommandTest(String className, String commandName, String pkg, String returnType) {
    return """
        package %s;

        import org.junit.jupiter.api.Test;
        import java.util.List;
        import static org.junit.jupiter.api.Assertions.*;

        class %sTest {

          private final %s command = new %s();

          @Test
          void preProcessParameters_returnsFalse() {
            assertFalse(command.preProcessParameters());
          }

          @Test
          void execute_withEmptyParams_throwsOrReturnsResult() {
            assertThrows(UnsupportedOperationException.class,
                () -> command.execute(List.of()));
          }
        }
        """.formatted(pkg, className, className, className);
  }

  private String toCommandName(String description) {
    String slug = description.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
    return slug.substring(0, Math.min(40, slug.length()));
  }

  private String toClassName(String commandName) {
    String[] parts = commandName.split("[-_]");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
    }
    return sb.toString();
  }
}
