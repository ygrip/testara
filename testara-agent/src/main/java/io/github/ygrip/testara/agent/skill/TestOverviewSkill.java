package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only skill: statistical overview of the entire test project.
 * No LLM required.
 */
public class TestOverviewSkill implements AgentSkill<Path, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
        Features: %d | ScenarioDefs: %d | Cases: %d | Outlines: %d | Examples: %d
        Steps: %d | StepDefs: %d | Commands: %d | Validators: %d | Tags: %d | AvgSteps: %.1f
        Tags: %s
        """,
        p.projectRoot(), p.buildTool() == null ? "unknown" : p.buildTool(), p.javaVersion(), p.mavenModules().size(),
        p.features().size(), p.totalScenarios(), p.totalExecutableCases(), outlines, p.totalExampleRows(),
        p.totalSteps(), p.stepDefinitions().size(), p.commands().size(),
        p.validations().size(), p.tags().size(), avg,
        p.tags().stream()
            .sorted(Comparator.comparingInt(TagIndex::executableCaseCount).reversed()
                .thenComparing(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
                .thenComparing(TagIndex::tag))
            .limit(15)
            .map(t -> t.tag() + ":scenarios=" + t.scenarioCount() + ",cases=" + t.executableCaseCount())
            .collect(Collectors.joining(" "))) + parseErrorsConcise(p);
  }

  private String renderMarkdown(TestaraProjectProfile p) {
    long outlines = p.features().stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    double avg = p.totalScenarios() > 0
        ? (double) p.totalSteps() / p.totalScenarios() : 0;

    StringBuilder sb = new StringBuilder();
    sb.append("## Testara Project Overview\n\n");
    sb.append(String.format("`%s` | %s | Java %s | %d modules\n\n",
        p.projectRoot(), p.buildTool() == null ? "unknown" : p.buildTool(), p.javaVersion(), p.mavenModules().size()));

    sb.append("| Metric | Count |\n|---|---|\n");
    sb.append("| Features | ").append(p.features().size()).append(" |\n");
    sb.append("| Scenario definitions | ").append(p.totalScenarios()).append(" |\n");
    sb.append("| Executable cases | ").append(p.totalExecutableCases()).append(" |\n");
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
          .sorted(Comparator.comparingInt(TagIndex::executableCaseCount).reversed()
              .thenComparing(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
              .thenComparing(TagIndex::tag))
          .limit(12)
          .map(t -> t.tag() + "(scenarios=" + t.scenarioCount() + ",cases=" + t.executableCaseCount() + ")")
          .collect(Collectors.joining(" ")));
      sb.append("\n");
    }
    if (!p.parseErrors().isEmpty()) {
      sb.append("\n**Parse errors (").append(p.parseErrors().size()).append(" files not indexed):**\n");
      p.parseErrors().forEach(error -> sb.append("- ").append(error).append("\n"));
    }
    return sb.toString();
  }

  /** Feature files that could not be parsed are missing from every count above — say so. */
  private String parseErrorsConcise(TestaraProjectProfile p) {
    if (p.parseErrors().isEmpty()) return "";
    return "ParseErrors: " + p.parseErrors().size() + " | " + String.join(" | ", p.parseErrors()) + "\n";
  }

  private String renderJson(TestaraProjectProfile p) {
    long outlines = p.features().stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    double avg = p.totalScenarios() > 0 ? (double) p.totalSteps() / p.totalScenarios() : 0;

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("project", p.projectRoot().toString());
    out.put("build", p.buildTool() == null ? "unknown" : p.buildTool().name());
    out.put("java", p.javaVersion());
    out.put("modules", p.mavenModules().size());
    out.put("features", p.features().size());
    out.put("scenarioDefinitions", p.totalScenarios());
    out.put("executableCases", p.totalExecutableCases());
    out.put("outlines", outlines);
    out.put("examples", p.totalExampleRows());
    out.put("steps", p.totalSteps());
    out.put("stepDefs", p.stepDefinitions().size());
    out.put("commands", p.commands().size());
    out.put("validators", p.validations().size());
    out.put("tags", p.tags().size());
    out.put("avgStepsPerScenario", Math.round(avg * 10.0) / 10.0);
    out.put("topTags", p.tags().stream()
        .sorted(Comparator.comparingInt(TagIndex::executableCaseCount).reversed()
            .thenComparing(Comparator.comparingInt(TagIndex::scenarioCount).reversed())
            .thenComparing(TagIndex::tag))
        .limit(15)
        .map(t -> Map.of(
            "tag", t.tag(),
            "scenarios", t.scenarioCount(),
            "cases", t.executableCaseCount(),
            "features", t.featureCount()))
        .toList());
    out.put("parseErrors", p.parseErrors());
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize test overview", e);
    }
  }

}
