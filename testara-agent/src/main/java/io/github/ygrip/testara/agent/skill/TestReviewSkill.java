package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.*;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;
import io.github.ygrip.testara.agent.parser.FeatureParser;
import io.github.ygrip.testara.agent.skill.review.ReviewFinding;
import io.github.ygrip.testara.agent.skill.review.ReviewSeverity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only skill: review feature files for quality issues.
 * Detects duplicates, weak assertions, complexity, and structural smells.
 */
public class TestReviewSkill implements AgentSkill<Path, String> {

  private static final int HIGH_COMPLEXITY_THRESHOLD = 10;
  private static final int SIMILAR_STEP_THRESHOLD = 3;

  private final FeatureParser parser = new FeatureParser();

  @Override
  public String name() { return "test-review"; }

  @Override
  public String execute(Path target, AgentContext context) {
    Path resolvedTarget = resolveTarget(target, context.projectRoot());
    List<FeatureIndex> features = loadFeatures(resolvedTarget);
    if (features.isEmpty()) return "No feature files found at: " + resolvedTarget
        + "\nproject-root: " + context.projectRoot()
        + "\nPath inputs may be absolute or relative to the project root.";

    List<ReviewFinding> findings = new ArrayList<>();
    findings.addAll(findDuplicateScenarioNames(features));
    findings.addAll(findScenariosWithoutThen(features));
    findings.addAll(findHighComplexityScenarios(features));
    findings.addAll(findScenariosWithNoTags(features));
    findings.addAll(findNearDuplicateStepSequences(features));
    findings.addAll(suggestBackgroundExtraction(features));
    findings.addAll(suggestScenarioOutlines(features));

    // Phase 6: flavor score using built-in step catalog
    List<FlavorEntry> flavorSteps = context.profile().flavorSteps();
    FlavorScore flavorScore = computeFlavorScore(features, flavorSteps);
    findings.addAll(flavorScore.migratableFindings());

    return renderMarkdown(findings, resolvedTarget, features, flavorScore);
  }

  // ── Flavor score ──────────────────────────────────────────────────

  private record FlavorScore(int total, int builtIn, int migratable, int custom,
      List<ReviewFinding> migratableFindings) {
    int score() { return total == 0 ? 100 : builtIn * 100 / total; }
  }

  private FlavorScore computeFlavorScore(List<FeatureIndex> features, List<FlavorEntry> catalog) {
    int total = 0, builtIn = 0, migratable = 0;
    List<ReviewFinding> findings = new ArrayList<>();

    for (FeatureIndex feature : features) {
      for (ScenarioIndex scenario : feature.scenarios()) {
        for (StepIndex step : scenario.steps()) {
          total++;
          String stepText = step.keyword() + " " + step.text();
          // Merge project-level catalog with the bundled framework catalog
          List<FlavorEntry> effectiveCatalog = catalog.isEmpty()
              ? FrameworkKnowledgeStore.instance().flavorCatalog() : catalog;
          boolean isBuiltIn = effectiveCatalog.stream()
              .anyMatch(e -> matchesFlavorStep(stepText, e));
          if (isBuiltIn) {
            builtIn++;
          } else {
            // Check if any flavor step COULD replace this generic step
            String generic = detectGenericPattern(step.text());
            if (generic != null) {
              migratable++;
              FlavorEntry suggested = catalog.stream()
                  .filter(e -> e.capability().contains(generic))
                  .findFirst().orElse(null);
              findings.add(new ReviewFinding(ReviewSeverity.INFO,
                  "MIGRATABLE: generic step where Testara built-in exists",
                  "Step `" + step.text() + "` could be replaced with a Testara built-in step."
                      + (suggested != null ? " Suggested: `" + suggested.example() + "`" : ""),
                  feature.path(), scenario.name(),
                  suggested != null ? "Replace with: " + suggested.example() : "Check testara-agent test-command --list for available steps"));
            }
          }
        }
      }
    }
    int custom = total - builtIn - migratable;
    return new FlavorScore(total, builtIn, migratable, custom, List.copyOf(findings));
  }

  // Step patterns are loaded from the bundled catalog; never hardcoded here.
  private static final List<java.util.regex.Pattern> BUILT_IN_PATTERNS =
      FrameworkKnowledgeStore.instance().uiStepPatterns();

  private boolean matchesFlavorStep(String stepText, FlavorEntry entry) {
    String lower = stepText.toLowerCase();
    // Check the bundled catalog patterns first (covers all slices from build-time scan)
    if (BUILT_IN_PATTERNS.stream().anyMatch(p -> p.matcher(lower).matches())) return true;
    // Then check the project-level runtime catalog entry
    try {
      String expr = entry.expression()
          .replace("(.+)", ".*").replace("(\\w+)", "\\w+")
          .replace("(\\d+)", "\\d+").replace("\"([^\"]*)\"", "\"[^\"]*\"")
          .replace("([^\"]*)", "[^\"]*").replaceAll("\\(\\|[^)]+\\)", "[^\\s]+")
          .replaceAll("\\\\", "");
      return stepText.toLowerCase().matches("(?i).*" + expr.toLowerCase() + ".*");
    } catch (Exception e) {
      return false;
    }
  }

  private String detectGenericPattern(String stepText) {
    String lower = stepText.toLowerCase();
    if (lower.contains("response status") || lower.contains("status code")) return "statuscode";
    if (lower.contains("process request") || lower.contains("api call")) return "process request";
    if (lower.contains("click") && !lower.contains("[ui]")) return "click";
    if (lower.contains("enter value") || lower.contains("type value")) return "enter value";
    if (lower.contains("sql") || lower.contains("execute query")) return "execute query";
    return null;
  }

  // ── Detectors ─────────────────────────────────────────────────────

  private List<ReviewFinding> findDuplicateScenarioNames(List<FeatureIndex> features) {
    Map<String, List<Path>> nameToFeatures = new LinkedHashMap<>();
    features.forEach(f -> f.scenarios().forEach(s -> {
      String key = s.name().strip().toLowerCase(Locale.ROOT);
      nameToFeatures.computeIfAbsent(key, k -> new ArrayList<>()).add(f.path());
    }));
    return nameToFeatures.entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .map(e -> new ReviewFinding(ReviewSeverity.HIGH,
            "Duplicate scenario name: \"" + e.getKey() + "\"",
            "Found in " + e.getValue().size() + " locations. Duplicate names hide coverage gaps "
                + "and make reports ambiguous.",
            e.getValue().get(0), e.getKey(),
            "Rename each scenario to reflect its specific intent."))
        .collect(Collectors.toList());
  }

  private List<ReviewFinding> findScenariosWithoutThen(List<FeatureIndex> features) {
    List<ReviewFinding> out = new ArrayList<>();
    for (FeatureIndex f : features) {
      for (ScenarioIndex s : f.scenarios()) {
        boolean hasThen = s.steps().stream()
            .anyMatch(step -> step.keyword().equalsIgnoreCase("Then"));
        if (!hasThen && !s.steps().isEmpty()) {
          out.add(new ReviewFinding(ReviewSeverity.HIGH,
              "Scenario has no Then assertion",
              "\"" + s.name() + "\" has steps but no Then. This scenario cannot verify any outcome.",
              f.path(), s.name(),
              "Add at least one Then step to assert expected behaviour."));
        }
      }
    }
    return out;
  }

  private List<ReviewFinding> findHighComplexityScenarios(List<FeatureIndex> features) {
    List<ReviewFinding> out = new ArrayList<>();
    for (FeatureIndex f : features) {
      for (ScenarioIndex s : f.scenarios()) {
        int steps = s.steps().size();
        if (steps > HIGH_COMPLEXITY_THRESHOLD) {
          out.add(new ReviewFinding(ReviewSeverity.MEDIUM,
              "High-complexity scenario (" + steps + " steps)",
              "\"" + s.name() + "\" has " + steps + " steps. Long scenarios are hard to maintain "
                  + "and pinpoint failures.",
              f.path(), s.name(),
              "Split into smaller focused scenarios or extract common steps into a Background."));
        }
      }
    }
    return out;
  }

  private List<ReviewFinding> findScenariosWithNoTags(List<FeatureIndex> features) {
    List<ReviewFinding> out = new ArrayList<>();
    for (FeatureIndex f : features) {
      if (!f.tags().isEmpty()) continue; // feature-level tag covers all
      for (ScenarioIndex s : f.scenarios()) {
        if (s.tags().isEmpty()) {
          out.add(new ReviewFinding(ReviewSeverity.LOW,
              "Scenario has no tags",
              "\"" + s.name() + "\" cannot be selected by tag filter, "
                  + "making targeted test runs impossible.",
              f.path(), s.name(),
              "Add at minimum a layer tag (@api / @ui) and a priority tag (@P0–@P4)."));
        }
      }
    }
    return out;
  }

  private List<ReviewFinding> findNearDuplicateStepSequences(List<FeatureIndex> features) {
    List<ScenarioIndex> allScenarios = features.stream()
        .flatMap(f -> f.scenarios().stream()).toList();
    List<ReviewFinding> out = new ArrayList<>();
    for (int i = 0; i < allScenarios.size(); i++) {
      for (int j = i + 1; j < allScenarios.size(); j++) {
        int common = countCommonSteps(allScenarios.get(i), allScenarios.get(j));
        int total  = allScenarios.get(i).steps().size();
        if (common >= SIMILAR_STEP_THRESHOLD && total > 0 && (double) common / total >= 0.7) {
          out.add(new ReviewFinding(ReviewSeverity.MEDIUM,
              "Near-duplicate step sequences",
              "\"" + allScenarios.get(i).name() + "\" and \"" + allScenarios.get(j).name()
                  + "\" share " + common + " identical steps.",
              null, allScenarios.get(i).name() + " / " + allScenarios.get(j).name(),
              "Consider a Scenario Outline or Background to deduplicate shared setup."));
        }
      }
    }
    return out;
  }

  private List<ReviewFinding> suggestBackgroundExtraction(List<FeatureIndex> features) {
    List<ReviewFinding> out = new ArrayList<>();
    for (FeatureIndex f : features) {
      if (f.scenarios().size() < 2) continue;
      if (!f.backgroundSteps().isEmpty()) continue;
      List<StepIndex> firstSteps = f.scenarios().get(0).steps();
      if (firstSteps.isEmpty()) continue;
      long sharesFirst = f.scenarios().stream()
          .filter(s -> !s.steps().isEmpty() && s.steps().get(0).text().equals(firstSteps.get(0).text()))
          .count();
      if (sharesFirst == f.scenarios().size()) {
        out.add(new ReviewFinding(ReviewSeverity.INFO,
            "Repeated setup step across all scenarios",
            "All " + f.scenarios().size() + " scenarios in \"" + f.featureName()
                + "\" share the same first step.",
            f.path(), null,
            "Extract the shared step into a Background block."));
      }
    }
    return out;
  }

  private List<ReviewFinding> suggestScenarioOutlines(List<FeatureIndex> features) {
    List<ReviewFinding> out = new ArrayList<>();
    for (FeatureIndex f : features) {
      Map<String, List<ScenarioIndex>> byStructure = new LinkedHashMap<>();
      for (ScenarioIndex s : f.scenarios()) {
        if (s.type() == ScenarioType.SCENARIO_OUTLINE) continue;
        String key = s.steps().stream().map(st -> st.keyword() + " " + st.text()
            .replaceAll("\"[^\"]+\"", "\"?\"")
            .replaceAll("\\d+", "N")).collect(Collectors.joining("|"));
        byStructure.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
      }
      for (var entry : byStructure.entrySet()) {
        List<ScenarioIndex> group = entry.getValue();
        if (group.size() < 2) continue;

        // Build suggested Examples table by extracting differing values
        List<List<String>> exampleRows = buildExampleTable(group);
        StringBuilder suggestion = new StringBuilder();
        suggestion.append("\"").append(group.get(0).name()).append("\" and ")
            .append(group.size() - 1).append(" other(s) share the same step pattern.\n\n");
        suggestion.append("**Suggested Scenario Outline conversion:**\n\n");

        ScenarioIndex first = group.get(0);
        suggestion.append("```gherkin\n");
        suggestion.append("Scenario Outline: ").append(extractCommonName(group)).append("\n");
        for (int si = 0; si < first.steps().size(); si++) {
          String text = parameterizeStep(first.steps().get(si).text(), exampleRows, si, group);
          suggestion.append("  ").append(first.steps().get(si).keyword())
              .append(" ").append(text).append("\n");
        }
        suggestion.append("\n  Examples:\n");
        // Header row from extracted parameters
        List<String> params = extractParameters(first.steps(), group);
        suggestion.append("    | ").append(String.join(" | ", params)).append(" |\n");
        // Data rows
        for (List<String> row : exampleRows) {
          suggestion.append("    | ").append(String.join(" | ", row)).append(" |\n");
        }
        suggestion.append("```\n");

        out.add(new ReviewFinding(ReviewSeverity.INFO,
            "Scenarios with identical step structure (" + group.size() + "\u00D7)",
            suggestion.toString(),
            f.path(), group.get(0).name(),
            "Convert to a Scenario Outline with an Examples table."));
      }
    }
    return out;
  }

  /** Build example table rows by extracting differing quoted/numeric values from steps. */
  private List<List<String>> buildExampleTable(List<ScenarioIndex> group) {
    List<List<String>> rows = new ArrayList<>();
    if (group.size() < 2) return rows;

    // Extract all quoted/numeric values from each scenario's steps
    for (ScenarioIndex s : group) {
      List<String> row = new ArrayList<>();
      for (var step : s.steps()) {
        // Extract quoted strings and numbers as example values
        var matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"|\\b(\\d+)\\b").matcher(step.text());
        while (matcher.find()) {
          row.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
      }
      if (!row.isEmpty()) rows.add(row);
    }
    return rows;
  }

  /** Parameterize a step text by replacing varying values with placeholders. */
  private String parameterizeStep(String stepText, List<List<String>> exampleRows,
      int stepIndex, List<ScenarioIndex> group) {
    if (exampleRows.isEmpty()) return stepText;

    // Extract all values from this step across all scenarios
    List<String> stepValues = new ArrayList<>();
    for (ScenarioIndex s : group) {
      var step = s.steps().size() > stepIndex ? s.steps().get(stepIndex) : null;
      if (step != null) {
        var matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"|\\b(\\d+)\\b").matcher(step.text());
        while (matcher.find()) {
          stepValues.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
      }
    }
    if (stepValues.isEmpty()) return stepText;

    // Replace varying values with parameter placeholders
    String result = stepText;
    for (String val : stepValues) {
      String paramName = val.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
      if (paramName.length() > 20) paramName = paramName.substring(0, 20);
      result = result.replace("\"" + val + "\"", "\"<" + paramName + ">\"");
      result = result.replaceAll("\\b" + java.util.regex.Pattern.quote(val) + "\\b",
          "<" + paramName + ">");
    }
    return result;
  }

  /** Extract parameter names from steps for the Examples header. */
  private List<String> extractParameters(List<StepIndex> steps, List<ScenarioIndex> group) {
    Set<String> params = new LinkedHashSet<>();
    for (ScenarioIndex s : group) {
      for (var step : s.steps()) {
        var matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"|\\b(\\d+)\\b").matcher(step.text());
        while (matcher.find()) {
          String val = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
          if (isVaryingAcrossScenarios(val, steps, group)) {
            String param = val.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
            if (param.length() > 20) param = param.substring(0, 20);
            params.add(param);
          }
        }
      }
    }
    if (params.isEmpty()) params.add("value");
    return List.copyOf(params);
  }

  private boolean isVaryingAcrossScenarios(String value, List<StepIndex> templateSteps,
      List<ScenarioIndex> group) {
    long unique = group.stream()
        .flatMap(s -> s.steps().stream())
        .map(st -> st.text())
        .filter(t -> t.contains(value))
        .distinct()
        .count();
    return unique > 1;
  }

  private String extractCommonName(List<ScenarioIndex> group) {
    // Find common words across scenario names
    String[] words = group.get(0).name().split("\\s+");
    if (words.length == 0) return "Combined Scenario";

    List<String> common = new ArrayList<>();
    for (String word : words) {
      if (group.stream().allMatch(s -> s.name().toLowerCase().contains(word.toLowerCase()))) {
        common.add(word);
      }
    }
    return common.isEmpty() ? group.get(0).name() + " Variations"
        : String.join(" ", common);
  }

  // ── Utilities ─────────────────────────────────────────────────────

  private int countCommonSteps(ScenarioIndex a, ScenarioIndex b) {
    Set<String> aTexts = a.steps().stream().map(s -> s.keyword() + s.text()).collect(Collectors.toSet());
    return (int) b.steps().stream().filter(s -> aTexts.contains(s.keyword() + s.text())).count();
  }

  private List<FeatureIndex> loadFeatures(Path target) {
    List<FeatureIndex> out = new ArrayList<>();
    try {
      if (Files.isRegularFile(target) && target.toString().endsWith(".feature")) {
        out.add(parser.parse(target));
      } else if (Files.isDirectory(target)) {
        try (Stream<Path> walk = Files.walk(target)) {
          walk.filter(p -> p.toString().endsWith(".feature"))
              .forEach(p -> { try { out.add(parser.parse(p)); } catch (IOException e) { /* skip */ } });
        }
      }
    } catch (IOException e) { /* return what we have */ }
    return out;
  }

  private Path resolveTarget(Path target, Path projectRoot) {
    Path effective = target == null ? Path.of(".") : target;
    if (effective.isAbsolute()) return effective.normalize();
    return projectRoot.resolve(effective).normalize();
  }

  private String renderMarkdown(List<ReviewFinding> findings, Path target,
      List<FeatureIndex> features, FlavorScore flavorScore) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Test Review: ").append(target.getFileName()).append("\n\n");
    sb.append("**Feature files reviewed:** ").append(features.size()).append("  \n");
    sb.append("**Total findings:** ").append(findings.size()).append("  \n\n");

    // Flavor score block
    sb.append("## Testara Flavor Score: ").append(flavorScore.score()).append("%\n\n");
    sb.append("| Metric | Count |\n|--------|-------|\n");
    sb.append("| Total steps | ").append(flavorScore.total()).append(" |\n");
    sb.append("| Built-in Testara steps | ").append(flavorScore.builtIn()).append(" |\n");
    sb.append("| Migratable (generic where built-in exists) | ").append(flavorScore.migratable()).append(" |\n");
    sb.append("| Custom steps | ").append(flavorScore.custom()).append(" |\n\n");
    if (flavorScore.score() >= 80) {
      sb.append("> Flavor score >= 80% — good Testara adoption.\n\n");
    } else {
      sb.append("> Flavor score below 80% — consider migrating generic steps to Testara built-ins.\n\n");
    }

    if (findings.isEmpty()) {
      sb.append("> No quality issues found.\n");
      return sb.toString();
    }

    for (ReviewSeverity sev : ReviewSeverity.values()) {
      List<ReviewFinding> group = findings.stream().filter(f -> f.severity() == sev).toList();
      if (group.isEmpty()) continue;
      sb.append("## ").append(sev).append(" (").append(group.size()).append(")\n\n");
      group.forEach(f -> sb.append(f.toMarkdown()).append("\n"));
    }
    long blocker = findings.stream().filter(f -> f.severity() == ReviewSeverity.BLOCKER).count();
    long high    = findings.stream().filter(f -> f.severity() == ReviewSeverity.HIGH).count();
    if (blocker > 0 || high > 0) {
      sb.append("## Priority Recommendations\n\n");
      if (blocker > 0) sb.append("- **P0** — Fix ").append(blocker).append(" BLOCKER issue(s).\n");
      if (high > 0)    sb.append("- **P1** — Resolve ").append(high).append(" HIGH issue(s).\n");
    }
    return sb.toString();
  }
}
