package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Query interface for structured access to cached project knowledge.
 *
 * <p>Skills use this instead of manually reading JSONL files.
 * Queries support text search and tag expression filtering.
 */
public interface KnowledgeQueryService {

  /** Find features matching given criteria. */
  List<FeatureIndex> findFeatures(KnowledgeQuery query);

  /** Find scenarios matching given criteria. */
  List<ScenarioIndex> findScenarios(KnowledgeQuery query, List<FeatureIndex> features);

  /** Find step definitions matching given criteria. */
  List<StepDefinitionIndex> findStepDefinitions(KnowledgeQuery query);

  /** Find tags matching given criteria. */
  List<TagIndex> findTags(KnowledgeQuery query);

  /** Find commands matching given criteria. */
  List<CommandIndex> findCommands(KnowledgeQuery query);

  /** Find validations matching given criteria. */
  List<ValidationIndex> findValidations(KnowledgeQuery query);

  /** Aggregate project-wide statistics. */
  ProjectOverviewStats overview(TestaraProjectProfile profile);
}
