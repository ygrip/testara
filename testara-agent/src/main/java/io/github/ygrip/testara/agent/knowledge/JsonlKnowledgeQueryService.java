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
    return profile.features().stream()
        .filter(f -> query.matchesText(f.featureName() + " " + f.path()))
        .filter(f -> query.tagExpression() == null || query.tagExpression().isBlank()
            || f.tags().stream().anyMatch(query::matchesTag)
            || f.scenarios().stream().anyMatch(s -> effectiveTags(f, s).stream().anyMatch(query::matchesTag)))
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
        .filter(entry -> query.tagExpression() == null || query.tagExpression().isBlank()
            || effectiveTags(entry.getKey(), entry.getValue()).stream().anyMatch(query::matchesTag))
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

  private Set<String> effectiveTags(FeatureIndex feature, ScenarioIndex scenario) {
    Set<String> tags = new LinkedHashSet<>(feature.tags());
    tags.addAll(scenario.tags());
    scenario.examples().forEach(ex -> tags.addAll(ex.tags()));
    return tags;
  }
}
