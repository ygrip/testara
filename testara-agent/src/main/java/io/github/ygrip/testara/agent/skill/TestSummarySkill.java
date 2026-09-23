package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.*;
import io.github.ygrip.testara.agent.parser.FeatureParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> IGNORED_DIRS = Set.of("target", ".git", ".testara-agent", "node_modules");
  private final FeatureParser parser = new FeatureParser();

  @Override
  public String name() { return "test-summary"; }

  @Override
  public String execute(Input input, AgentContext context) {
    List<FeatureIndex> features = loadFeatures(input.target());
    if (features.isEmpty()) return "No feature files found at: " + input.target();

    List<FeatureIndex> filtered = input.scenarioFilter() != null && !input.scenarioFilter().isBlank()
        ? features.stream()
            .map(f -> filterScenarios(f, input.scenarioFilter()))
            .filter(f -> !f.scenarios().isEmpty())
            .toList()
        : features;

    boolean concise = "true".equals(context.options().getOrDefault("concise", "false"));
    boolean asJson  = "json".equals(context.options().getOrDefault("format", ""));
    if (asJson) return renderJson(filtered, input.target());
    return concise ? renderConcise(filtered, input.target()) : renderMarkdown(filtered, input.target());
  }

  private List<FeatureIndex> loadFeatures(Path target) {
    List<FeatureIndex> out = new ArrayList<>();
    try {
      if (Files.isRegularFile(target) && target.toString().endsWith(".feature")) {
        out.add(parser.parse(target));
      } else if (Files.isDirectory(target)) {
        try (Stream<Path> walk = Files.walk(target)) {
          walk.filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".feature"))
              .filter(p -> !containsIgnoredDirectory(target, p))
              .sorted()
              .forEach(p -> {
                try { out.add(parser.parse(p)); }
                catch (IOException e) { /* malformed files are skipped by summary; review surfaces details */ }
              });
        }
      }
    } catch (IOException e) {
      // return what we have
    }
    return out;
  }

  private boolean containsIgnoredDirectory(Path root, Path candidate) {
    Path relative;
    try {
      relative = root.toAbsolutePath().normalize().relativize(candidate.toAbsolutePath().normalize());
    } catch (IllegalArgumentException e) {
      relative = candidate;
    }
    for (Path part : relative) {
      if (IGNORED_DIRS.contains(part.toString())) return true;
    }
    return false;
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

  /** Minimal token-efficient output. */
  private String renderConcise(List<FeatureIndex> features, Path target) {
    int scenarios = features.stream().mapToInt(f -> f.scenarios().size()).sum();
    long outlines = features.stream().flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    Set<String> tags = new TreeSet<>();
    features.forEach(f -> {
      tags.addAll(f.tags());
      f.scenarios().forEach(s -> tags.addAll(s.tags()));
    });

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%s | %d features | %d scenarios",
        target.getFileName(), features.size(), scenarios));
    if (outlines > 0) sb.append(" | ").append(outlines).append(" outlines");
    sb.append("\n");
    features.forEach(f -> {
      sb.append("  ").append(f.featureName());
      if (!f.tags().isEmpty()) sb.append(" [").append(String.join(" ", f.tags())).append("]");
      sb.append("\n");
      f.scenarios().forEach(s ->
          sb.append("    ").append(s.type() == ScenarioType.SCENARIO_OUTLINE ? "◉ " : "◦ ")
              .append(s.name()).append(" (").append(s.steps().size()).append(" steps)\n"));
    });
    return sb.toString();
  }

  private String renderJson(List<FeatureIndex> features, Path target) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("target", target.toString());
    out.put("featureFiles", features.size());
    out.put("scenarios", features.stream().mapToInt(f -> f.scenarios().size()).sum());
    out.put("features", features.stream().map(f -> {
      Map<String, Object> feature = new LinkedHashMap<>();
      feature.put("feature", f.featureName());
      feature.put("file", f.path().toString());
      feature.put("tags", f.tags());
      feature.put("scenarios", f.scenarios().stream().map(s -> {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("name", s.name());
        scenario.put("type", s.type().name());
        scenario.put("tags", s.tags());
        scenario.put("steps", s.steps().size());
        scenario.put("examples", s.examples().stream().map(ex -> Map.of(
            "tags", ex.tags(),
            "headers", ex.headers(),
            "rows", ex.rowCount()
        )).toList());
        return scenario;
      }).toList());
      return feature;
    }).toList());
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize test summary", e);
    }
  }}
