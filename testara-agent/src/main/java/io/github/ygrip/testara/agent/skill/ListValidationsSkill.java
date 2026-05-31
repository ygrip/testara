package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.ValidationIndex;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only skill: lists all Testara validations discovered in the project with their metadata.
 * Agent-friendly output — no LLM required.
 */
public class ListValidationsSkill implements AgentSkill<Void, String> {

  @Override
  public String name() { return "list-validations"; }

  @Override
  public String execute(Void input, AgentContext context) {
    List<ValidationIndex> validations = context.profile().validations().stream()
        .sorted(Comparator.comparing(ValidationIndex::validation))
        .collect(Collectors.toList());

    String format = context.options().getOrDefault("format", "markdown");
    return switch (format) {
      case "json"    -> renderJson(validations);
      case "concise" -> renderConcise(validations);
      default        -> renderMarkdown(validations);
    };
  }

  private String renderMarkdown(List<ValidationIndex> validations) {
    if (validations.isEmpty()) {
      return "# Available Testara Validations\n\nNo validations found in project source.\n";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("# Available Testara Validations\n\n");
    sb.append("Found **").append(validations.size()).append("** validation(s).\n\n");
    sb.append("| Validation | Aliases | Class |\n");
    sb.append("|-----------|---------|-------|\n");
    for (ValidationIndex v : validations) {
      sb.append("| `").append(v.validation()).append("` | ");
      sb.append(v.aliases().isEmpty() ? "—" : v.aliases().stream().map(a -> "`" + a + "`").collect(Collectors.joining(", ")));
      sb.append(" | `").append(v.className()).append("` |\n");
    }
    sb.append("\n## Usage\n\n");
    sb.append("Validations are used in JSON validation files or step expressions:\n\n");
    sb.append("```json\n{\n  \"validation\": \"VALIDATION_NAME\",\n  \"actual\": \"${someCommand()}\",\n  \"expected\": \"expectedValue\"\n}\n```\n");
    return sb.toString();
  }

  private String renderConcise(List<ValidationIndex> validations) {
    if (validations.isEmpty()) return "no validations indexed. Run test-validation 'description' to generate one.";
    StringBuilder sb = new StringBuilder();
    sb.append(validations.size()).append(" validations. detail: test-validation detail:<name>\n");
    sb.append(validations.stream()
        .sorted(Comparator.comparing(ValidationIndex::validation))
        .map(v -> v.validation() + "(" + v.actualType() + "→" + v.expectedType() + ")")
        .collect(Collectors.joining(", ")));
    return sb.toString();
  }

  private String renderJson(List<ValidationIndex> validations) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"validations\": [\n");
    for (int i = 0; i < validations.size(); i++) {
      ValidationIndex v = validations.get(i);
      sb.append("    {\n");
      sb.append("      \"name\": \"").append(v.validation()).append("\",\n");
      sb.append("      \"aliases\": [").append(jsonStringArray(v.aliases())).append("],\n");
      sb.append("      \"className\": \"").append(v.className()).append("\",\n");
      sb.append("      \"sourcePath\": \"").append(v.sourcePath()).append("\"\n");
      sb.append("    }").append(i < validations.size() - 1 ? "," : "").append("\n");
    }
    sb.append("  ]\n}");
    return sb.toString();
  }

  private String jsonStringArray(List<String> items) {
    return items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
  }
}
