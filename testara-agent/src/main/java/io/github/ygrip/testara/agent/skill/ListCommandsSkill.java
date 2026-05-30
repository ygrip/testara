package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.CommandIndex;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only skill: lists all Testara commands discovered in the project with their metadata.
 * Agent-friendly output — no LLM required.
 */
public class ListCommandsSkill implements AgentSkill<Void, String> {

  @Override
  public String name() { return "list-commands"; }

  @Override
  public String execute(Void input, AgentContext context) {
    List<CommandIndex> commands = context.profile().commands().stream()
        .sorted(Comparator.comparing(CommandIndex::command))
        .collect(Collectors.toList());

    String format = context.options().getOrDefault("format", "markdown");
    return "json".equals(format) ? renderJson(commands) : renderMarkdown(commands);
  }

  private String renderMarkdown(List<CommandIndex> commands) {
    if (commands.isEmpty()) {
      return "# Available Testara Commands\n\nNo commands found in project source.\n";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("# Available Testara Commands\n\n");
    sb.append("Found **").append(commands.size()).append("** command(s).\n\n");
    sb.append("| Command | Aliases | Sub-commands | Cacheable | Class |\n");
    sb.append("|---------|---------|-------------|-----------|-------|\n");
    for (CommandIndex c : commands) {
      sb.append("| `").append(c.command()).append("` | ");
      sb.append(c.aliases().isEmpty() ? "—" : c.aliases().stream().map(a -> "`" + a + "`").collect(Collectors.joining(", ")));
      sb.append(" | ").append("—");  // subCommands not in CommandIndex — shown via class
      sb.append(" | ").append(c.cacheable() ? "yes" : "no");
      sb.append(" | `").append(c.className()).append("` |\n");
    }
    sb.append("\n## Usage\n\n");
    sb.append("Commands are used inside Testara step expressions:\n\n");
    sb.append("```\n${commandName(param1, param2)}\n```\n\n");
    sb.append("Aliases are interchangeable with the primary command name.\n");
    return sb.toString();
  }

  private String renderJson(List<CommandIndex> commands) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"commands\": [\n");
    for (int i = 0; i < commands.size(); i++) {
      CommandIndex c = commands.get(i);
      sb.append("    {\n");
      sb.append("      \"name\": \"").append(c.command()).append("\",\n");
      sb.append("      \"aliases\": [").append(jsonStringArray(c.aliases())).append("],\n");
      sb.append("      \"cacheable\": ").append(c.cacheable()).append(",\n");
      sb.append("      \"className\": \"").append(c.className()).append("\",\n");
      sb.append("      \"sourcePath\": \"").append(c.sourcePath()).append("\"\n");
      sb.append("    }").append(i < commands.size() - 1 ? "," : "").append("\n");
    }
    sb.append("  ]\n}");
    return sb.toString();
  }

  private String jsonStringArray(List<String> items) {
    return items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
  }
}
