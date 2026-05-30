package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.*;
import io.github.ygrip.testara.agent.parser.FeatureParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Read-only skill: summarize a feature file, scenario, or directory.
 * No LLM required.
 */
public class TestSummarySkill implements AgentSkill<TestSummarySkill.Input, String> {

  public record Input(Path target, String scenarioFilter) {}

  private final FeatureParser parser = new FeatureParser();

  @Override
  public String name() { return "test-summary"; }

  @Override
  public String execute(Input input, AgentContext context) {
    List<FeatureIndex> features = loadFeatures(input.target());
    if (features.isEmpty()) return "No feature files found at: " + input.target();

    List<FeatureIndex> filtered = input.scenarioFilter() != null && !input.scenarioFilter().isBlank()
        ? features.stream().map(f -> filterScenarios(f, input.scenarioFilter())).toList()
        : features;

    return renderMarkdown(filtered, input.target());
  }

  private List<FeatureIndex> loadFeatures(Path target) {
    List<FeatureIndex> out = new ArrayList<>();
    try {
      if (Files.isRegularFile(target) && target.toString().endsWith(".feature")) {
        out.add(parser.parse(target));
      } else if (Files.isDirectory(target)) {
        try (Stream<Path> walk = Files.walk(target)) {
          walk.filter(p -> p.toString().endsWith(".feature"))
              .forEach(p -> {
                try { out.add(parser.parse(p)); }
                catch (IOException e) { /* skip */ }
              });
        }
      }
    } catch (IOException e) {
      // return what we have
    }
    return out;
  }

  private FeatureIndex filterScenarios(FeatureIndex f, String filter) {
    String lc = filter.toLowerCase(Locale.ROOT);
    List<ScenarioIndex> matched = f.scenarios().stream()
        .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(lc))
        .toList();
    return new FeatureIndex(f.path(), f.featureName(), f.tags(), matched, f.backgroundSteps());
  }

  private String renderMarkdown(List<FeatureIndex> features, Path target) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Test Summary: ").append(target.getFileName()).append("\n\n");

    int totalScenarios = features.stream().mapToInt(f -> f.scenarios().size()).sum();
    long totalOutlines = features.stream()
        .flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    long totalExamples = features.stream().mapToLong(FeatureIndex::totalExampleRows).sum();

    sb.append("**Feature files:** ").append(features.size()).append("  \n");
    sb.append("**Scenarios:** ").append(totalScenarios).append("  \n");
    if (totalOutlines > 0) {
      sb.append("**Scenario Outlines:** ").append(totalOutlines)
          .append(" (").append(totalExamples).append(" example rows)  \n");
    }

    Set<String> allTags = new TreeSet<>();
    features.forEach(f -> {
      allTags.addAll(f.tags());
      f.scenarios().forEach(s -> allTags.addAll(s.tags()));
    });
    if (!allTags.isEmpty()) {
      sb.append("**Tags:** ").append(String.join(", ", allTags)).append("  \n");
    }
    sb.append("\n");

    for (FeatureIndex f : features) {
      sb.append("## ").append(f.featureName()).append("\n");
      if (!f.tags().isEmpty()) {
        sb.append("Tags: ").append(String.join(" ", f.tags())).append("\n");
      }
      sb.append("`").append(f.path()).append("`\n\n");

      if (!f.backgroundSteps().isEmpty()) {
        sb.append("**Background steps:**\n");
        f.backgroundSteps().forEach(s -> {
            sb.append("- ").append(s.keyword()).append(" ").append(s.text()).append("\n");
            if (!s.dataTable().isEmpty()) {
              for (List<String> row : s.dataTable()) {
                sb.append("    | ").append(String.join(" | ", row)).append(" |\n");
              }
            }
          });
        sb.append("\n");
      }

      for (ScenarioIndex s : f.scenarios()) {
        sb.append("### ").append(s.name());
        if (s.type() == ScenarioType.SCENARIO_OUTLINE) sb.append(" *(Outline)*");
        sb.append("\n");
        if (!s.tags().isEmpty()) {
          sb.append("Tags: ").append(String.join(" ", s.tags())).append("\n");
        }
        s.steps().forEach(step -> {
            sb.append("- ").append(step.keyword()).append(" ").append(step.text()).append("\n");
            if (!step.dataTable().isEmpty()) {
              for (List<String> row : step.dataTable()) {
                sb.append("    | ").append(String.join(" | ", row)).append(" |\n");
              }
            }
          });
        s.examples().forEach(ex ->
            sb.append("  - Examples: ").append(ex.rowCount()).append(" rows (")
                .append(String.join(", ", ex.headers())).append(")\n"));
        sb.append("\n");
      }
    }
    return sb.toString();
  }
}
