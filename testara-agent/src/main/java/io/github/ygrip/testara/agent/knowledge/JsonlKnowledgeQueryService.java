package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory query implementation over a cached Testara project profile.
 */
public class JsonlKnowledgeQueryService implements KnowledgeQueryService {

  private final TestaraProjectProfile profile;

  public JsonlKnowledgeQueryService() {
    this(null);
  }

  public JsonlKnowledgeQueryService(TestaraProjectProfile profile) {
    this.profile = profile;
  }

  @Override
  public List<FeatureIndex> findFeatures(KnowledgeQuery query) {
    if (profile == null) return List.of();
    // A feature matches a tag expression only through a scenario (or Examples block) it would run.
    return profile.features().stream()
        .filter(f -> query.matchesText(f.featureName() + " " + f.path()))
        .filter(f -> !query.hasTagExpression()
            || f.scenarios().stream().anyMatch(s -> matchesScenarioTags(query, f, s)))
        .limit(query.maxResults())
        .toList();
  }

  @Override
  public List<ScenarioIndex> findScenarios(KnowledgeQuery query,
      List<FeatureIndex> features) {
    if (features == null) return List.of();
    return features.stream()
        .flatMap(f -> f.scenarios().stream().map(s -> Map.entry(f, s)))
        .filter(entry -> query.matchesText(entry.getValue().name()))
        .filter(entry -> !query.hasTagExpression()
            || matchesScenarioTags(query, entry.getKey(), entry.getValue()))
        .limit(query.maxResults())
        .map(Map.Entry::getValue)
        .collect(Collectors.toList());
  }

  @Override
  public List<StepDefinitionIndex> findStepDefinitions(KnowledgeQuery query) {
    if (profile == null) return List.of();
    return profile.stepDefinitions().stream()
        .filter(s -> query.matchesText(String.join(" ",
            s.annotation(), s.expression(), s.className(), String.valueOf(s.sourcePath()))))
        .limit(query.maxResults())
        .toList();
  }

  @Override
  public List<TagIndex> findTags(KnowledgeQuery query) {
    if (profile == null) return List.of();
    return profile.tags().stream()
        .filter(t -> query.matchesText(t.tag()))
        .filter(t -> query.matchesTag(t.tag()))
        .limit(query.maxResults())
        .toList();
  }

  @Override
  public List<CommandIndex> findCommands(KnowledgeQuery query) {
    if (profile == null) return List.of();
    return profile.commands().stream()
        .filter(c -> query.matchesText(String.join(" ",
            c.command(), String.join(" ", c.aliases()), c.className(), c.returnType())))
        .limit(query.maxResults())
        .toList();
  }

  @Override
  public List<ValidationIndex> findValidations(KnowledgeQuery query) {
    if (profile == null) return List.of();
    return profile.validations().stream()
        .filter(v -> query.matchesText(String.join(" ",
            v.validation(), String.join(" ", v.aliases()), v.className(),
            v.actualType(), v.expectedType())))
        .limit(query.maxResults())
        .toList();
  }

  @Override
  public ProjectOverviewStats overview(TestaraProjectProfile p) {
    if (p == null) return ProjectOverviewStats.empty();
    long outlines = p.features().stream()
        .flatMap(f -> f.scenarios().stream())
        .filter(s -> s.type() == ScenarioType.SCENARIO_OUTLINE).count();
    double avg = p.totalScenarios() > 0
        ? (double) p.totalSteps() / p.totalScenarios() : 0.0;

    return new ProjectOverviewStats(
        p.features().size(), p.totalScenarios(), (int) outlines,
        p.totalExampleRows(), p.totalSteps(),
        p.stepDefinitions().size(), p.commands().size(),
        p.validations().size(), p.tags().size(), avg);
  }

  /**
   * Evaluates like Cucumber does per pickle: feature + scenario (incl. inherited Rule) tags, plus the
   * tags of one Examples block at a time — tags of different Examples blocks are never merged.
   */
  private boolean matchesScenarioTags(KnowledgeQuery query, FeatureIndex feature, ScenarioIndex scenario) {
    Set<String> baseTags = new LinkedHashSet<>(feature.tags());
    baseTags.addAll(scenario.tags());
    if (scenario.examples().isEmpty()) return query.matchesTags(baseTags);
    for (ExamplesIndex examples : scenario.examples()) {
      Set<String> caseTags = new LinkedHashSet<>(baseTags);
      caseTags.addAll(examples.tags());
      if (query.matchesTags(caseTags)) return true;
    }
    return false;
  }
}
