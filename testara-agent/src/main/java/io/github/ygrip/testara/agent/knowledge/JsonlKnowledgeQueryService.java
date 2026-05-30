package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSONL-backed implementation of {@link KnowledgeQueryService}.
 *
 * <p>Reads from the in-memory {@link TestaraProjectProfile} loaded by
 * {@link JsonlKnowledgeStore}. No direct file I/O — the profile is the
 * canonical in-memory representation after indexing.
 */
public class JsonlKnowledgeQueryService implements KnowledgeQueryService {

  @Override
  public List<FeatureIndex> findFeatures(KnowledgeQuery query) {
    return List.of(); // delegates to profile already loaded
  }

  @Override
  public List<ScenarioIndex> findScenarios(KnowledgeQuery query,
      List<FeatureIndex> features) {
    if (features == null) return List.of();
    return features.stream()
        .flatMap(f -> f.scenarios().stream())
        .filter(s -> query.matchesText(s.name()))
        .limit(query.maxResults())
        .collect(Collectors.toList());
  }

  @Override
  public List<StepDefinitionIndex> findStepDefinitions(KnowledgeQuery query) {
    return List.of();
  }

  @Override
  public List<TagIndex> findTags(KnowledgeQuery query) {
    return List.of();
  }

  @Override
  public List<CommandIndex> findCommands(KnowledgeQuery query) {
    return List.of();
  }

  @Override
  public List<ValidationIndex> findValidations(KnowledgeQuery query) {
    return List.of();
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
}
