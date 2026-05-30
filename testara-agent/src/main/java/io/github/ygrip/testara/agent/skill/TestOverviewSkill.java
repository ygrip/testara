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
    String format = context.options().getOrDefault("format", "markdown");
    boolean concise = "true".equals(context.options().getOrDefault("concise", "false"));

    return switch (format) {
      case "json"   -> renderJson(profile);
      case "concise", "text" -> renderConcise(profile);
      default       -> concise ? renderConcise(profile) : renderMarkdown(profile);
    };
  }

  /** Minimal token-efficient output for AI assistant consumption. */
  private String renderConcise(TestaraProjectProfile p) {
    long outlines = p.features().stream()
        .flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    double avg = p.totalScenarios() > 0
        ? (double) p.totalSteps() / p.totalScenarios() : 0;

    return String.format("""
        Project: %s | Build: %s | Java: %s | Modules: %d
        Features: %d | Scenarios: %d | Outlines: %d | Examples: %d
        Steps: %d | StepDefs: %d | Commands: %d | Validators: %d | Tags: %d | AvgSteps: %.1f
        Tags: %s
        """,
        p.projectRoot(), p.buildTool(), p.javaVersion(), p.mavenModules().size(),
        p.features().size(), p.totalScenarios(), outlines, p.totalExampleRows(),
        p.totalSteps(), p.stepDefinitions().size(), p.commands().size(),
        p.validations().size(), p.tags().size(), avg,
        p.tags().stream().sorted(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
            .limit(15).map(t -> t.tag() + ":" + t.scenarioCount())
            .collect(Collectors.joining(" ")));
  }

  private String renderMarkdown(TestaraProjectProfile p) {
    long outlines = p.features().stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    double avg = p.totalScenarios() > 0
        ? (double) p.totalSteps() / p.totalScenarios() : 0;

    StringBuilder sb = new StringBuilder();
    sb.append("## Testara Project Overview\n\n");
    sb.append(String.format("`%s` | %s | Java %s | %d modules\n\n",
        p.projectRoot(), p.buildTool(), p.javaVersion(), p.mavenModules().size()));

    sb.append("| Metric | Count |\n|---|---|\n");
    sb.append("| Features | ").append(p.features().size()).append(" |\n");
    sb.append("| Scenarios | ").append(p.totalScenarios()).append(" |\n");
    sb.append("| Outlines | ").append(outlines).append(" |\n");
    sb.append("| Example rows | ").append(p.totalExampleRows()).append(" |\n");
    sb.append("| Steps | ").append(p.totalSteps()).append(" |\n");
    sb.append("| Step defs | ").append(p.stepDefinitions().size()).append(" |\n");
    sb.append("| Commands | ").append(p.commands().size()).append(" |\n");
    sb.append("| Validators | ").append(p.validations().size()).append(" |\n");
    sb.append("| Avg steps/scenario | ").append(String.format("%.1f", avg)).append(" |\n");

    if (!p.tags().isEmpty()) {
      sb.append("\n**Top tags:** ");
      sb.append(p.tags().stream()
          .sorted(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
          .limit(12)
          .map(t -> t.tag() + "(" + t.scenarioCount() + ")")
          .collect(Collectors.joining(" ")));
      sb.append("\n");
    }
    return sb.toString();
  }

  private String renderJson(TestaraProjectProfile p) {
    long outlines = p.features().stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    double avg = p.totalScenarios() > 0 ? (double) p.totalSteps() / p.totalScenarios() : 0;

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("project", p.projectRoot().toString());
    out.put("build", p.buildTool().name());
    out.put("java", p.javaVersion());
    out.put("modules", p.mavenModules().size());
    out.put("features", p.features().size());
    out.put("scenarios", p.totalScenarios());
    out.put("outlines", outlines);
    out.put("examples", p.totalExampleRows());
    out.put("steps", p.totalSteps());
    out.put("stepDefs", p.stepDefinitions().size());
    out.put("commands", p.commands().size());
    out.put("validators", p.validations().size());
    out.put("tags", p.tags().size());
    out.put("avgStepsPerScenario", Math.round(avg * 10.0) / 10.0);
    out.put("topTags", p.tags().stream()
        .sorted(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
        .limit(15).map(t -> t.tag() + ":" + t.scenarioCount()).toList());
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
