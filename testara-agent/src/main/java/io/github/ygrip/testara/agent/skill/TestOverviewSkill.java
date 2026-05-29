package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only skill: statistical overview of the entire test project.
 * No LLM required.
 */
public class TestOverviewSkill implements AgentSkill<Path, String> {

  @Override
  public String name() { return "test-overview"; }

  @Override
  public String execute(Path target, AgentContext context) {
    TestaraProjectProfile profile = context.profile();
    return switch (context.options().getOrDefault("format", "markdown")) {
      case "json" -> renderJson(profile);
      default     -> renderMarkdown(profile);
    };
  }

  private String renderMarkdown(TestaraProjectProfile p) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Project Overview\n\n");
    sb.append("**Project root:** `").append(p.projectRoot()).append("`  \n");
    sb.append("**Build tool:** ").append(p.buildTool()).append("  \n");
    sb.append("**Java version:** ").append(p.javaVersion()).append("  \n");
    if (!p.mavenModules().isEmpty()) {
      sb.append("**Maven modules:** ").append(p.mavenModules().size()).append("  \n");
    }
    sb.append("\n");

    sb.append("## Coverage\n\n");
    sb.append("| Metric | Count |\n|---|---|\n");
    sb.append("| Feature files | ").append(p.features().size()).append(" |\n");
    sb.append("| Scenarios | ").append(p.totalScenarios()).append(" |\n");

    long outlines = p.features().stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    sb.append("| Scenario Outlines | ").append(outlines).append(" |\n");
    sb.append("| Example rows | ").append(p.totalExampleRows()).append(" |\n");
    sb.append("| Total steps | ").append(p.totalSteps()).append(" |\n");
    sb.append("| Step definitions | ").append(p.stepDefinitions().size()).append(" |\n");
    sb.append("| Custom commands | ").append(p.commands().size()).append(" |\n");
    sb.append("| Custom validators | ").append(p.validations().size()).append(" |\n");

    if (p.totalScenarios() > 0) {
      double avgSteps = (double) p.totalSteps() / p.totalScenarios();
      sb.append("| Avg steps/scenario | ").append(String.format("%.1f", avgSteps)).append(" |\n");
    }
    sb.append("\n");

    if (!p.tags().isEmpty()) {
      sb.append("## Tag Distribution\n\n");
      sb.append("| Tag | Features | Scenarios |\n|---|---|---|\n");
      p.tags().stream()
          .sorted(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
          .limit(20)
          .forEach(t -> sb.append("| ").append(t.tag())
              .append(" | ").append(t.featureCount())
              .append(" | ").append(t.scenarioCount()).append(" |\n"));
      sb.append("\n");
    }

    if (!p.commands().isEmpty()) {
      sb.append("## Custom Commands\n\n");
      p.commands().forEach(c -> sb.append("- `").append(c.command()).append("`")
          .append(c.aliases().isEmpty() ? "" : " (aliases: " + String.join(", ", c.aliases()) + ")")
          .append("\n"));
      sb.append("\n");
    }

    if (!p.validations().isEmpty()) {
      sb.append("## Custom Validators\n\n");
      p.validations().forEach(v -> sb.append("- `").append(v.validation()).append("`")
          .append(v.aliases().isEmpty() ? "" : " (aliases: " + String.join(", ", v.aliases()) + ")")
          .append("\n"));
      sb.append("\n");
    }

    // Longest scenarios
    List<ScenarioIndex> longest = p.features().stream()
        .flatMap(f -> f.scenarios().stream())
        .sorted(Comparator.comparingInt((ScenarioIndex s) -> s.steps().size()).reversed())
        .limit(5)
        .toList();
    if (!longest.isEmpty() && longest.get(0).steps().size() > 5) {
      sb.append("## Longest Scenarios\n\n");
      longest.forEach(s -> sb.append("- `").append(s.name())
          .append("` (").append(s.steps().size()).append(" steps)\n"));
      sb.append("\n");
    }

    return sb.toString();
  }

  private String renderJson(TestaraProjectProfile p) {
    long outlines = p.features().stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("projectRoot", p.projectRoot().toString());
    out.put("buildTool", p.buildTool().name());
    out.put("javaVersion", p.javaVersion());
    out.put("mavenModules", p.mavenModules().size());
    out.put("featureFiles", p.features().size());
    out.put("scenarios", p.totalScenarios());
    out.put("scenarioOutlines", outlines);
    out.put("exampleRows", p.totalExampleRows());
    out.put("totalSteps", p.totalSteps());
    out.put("stepDefinitions", p.stepDefinitions().size());
    out.put("customCommands", p.commands().size());
    out.put("customValidators", p.validations().size());

    Map<String, Integer> tagDist = p.tags().stream()
        .collect(Collectors.toMap(TagIndex::tag, TagIndex::scenarioCount,
            (a, b) -> a, LinkedHashMap::new));
    out.put("tagDistribution", tagDist);

    // Simple JSON serialisation without external library
    return toJson(out, 0);
  }

  @SuppressWarnings("unchecked")
  private String toJson(Object obj, int indent) {
    String pad = "  ".repeat(indent);
    String inner = "  ".repeat(indent + 1);
    if (obj instanceof Map<?, ?> map) {
      if (map.isEmpty()) return "{}";
      StringJoiner sj = new StringJoiner(",\n" + inner, "{\n" + inner, "\n" + pad + "}");
      map.forEach((k, v) -> sj.add("\"" + k + "\": " + toJson(v, indent + 1)));
      return sj.toString();
    }
    if (obj instanceof List<?> list) {
      if (list.isEmpty()) return "[]";
      StringJoiner sj = new StringJoiner(", ", "[", "]");
      list.forEach(item -> sj.add(toJson(item, indent)));
      return sj.toString();
    }
    if (obj instanceof String s) return "\"" + s.replace("\"", "\\\"") + "\"";
    return String.valueOf(obj);
  }
}
