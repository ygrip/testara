package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.*;
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
    List<FeatureIndex> features = loadFeatures(target);
    if (features.isEmpty()) return "No feature files found at: " + target;

    List<ReviewFinding> findings = new ArrayList<>();
    findings.addAll(findDuplicateScenarioNames(features));
    findings.addAll(findScenariosWithoutThen(features));
    findings.addAll(findHighComplexityScenarios(features));
    findings.addAll(findScenariosWithNoTags(features));
    findings.addAll(findNearDuplicateStepSequences(features));
    findings.addAll(suggestBackgroundExtraction(features));
    findings.addAll(suggestScenarioOutlines(features));

    return renderMarkdown(findings, target, features);
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
      byStructure.values().stream()
          .filter(group -> group.size() >= 2)
          .forEach(group -> out.add(new ReviewFinding(ReviewSeverity.INFO,
              "Scenarios with identical step structure (" + group.size() + "×)",
              "\"" + group.get(0).name() + "\" and " + (group.size() - 1) + " others share the same step pattern.",
              f.path(), group.get(0).name(),
              "Convert to a Scenario Outline with Examples table.")));
    }
    return out;
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

  private String renderMarkdown(List<ReviewFinding> findings, Path target, List<FeatureIndex> features) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Test Review: ").append(target.getFileName()).append("\n\n");
    sb.append("**Feature files reviewed:** ").append(features.size()).append("  \n");
    sb.append("**Total findings:** ").append(findings.size()).append("  \n\n");

    if (findings.isEmpty()) {
      sb.append("> No issues found. Looks good!\n");
      return sb.toString();
    }

    for (ReviewSeverity sev : ReviewSeverity.values()) {
      List<ReviewFinding> group = findings.stream()
          .filter(f -> f.severity() == sev).toList();
      if (group.isEmpty()) continue;
      sb.append("## ").append(sev).append(" (").append(group.size()).append(")\n\n");
      group.forEach(f -> sb.append(f.toMarkdown()).append("\n"));
    }
    // Priority recommendations
    long blocker = findings.stream().filter(f -> f.severity() == ReviewSeverity.BLOCKER).count();
    long high     = findings.stream().filter(f -> f.severity() == ReviewSeverity.HIGH).count();
    if (blocker > 0 || high > 0) {
      sb.append("## Priority Recommendations\n\n");
      if (blocker > 0) sb.append("- **P0** — Fix ").append(blocker).append(" BLOCKER issue(s) before any release.\n");
      if (high > 0)    sb.append("- **P1** — Resolve ").append(high).append(" HIGH issue(s) in the next sprint.\n");
      sb.append("- **P2–P3** — Schedule remaining MEDIUM/LOW findings for backlog grooming.\n\n");
    }
    return sb.toString();
  }
}
