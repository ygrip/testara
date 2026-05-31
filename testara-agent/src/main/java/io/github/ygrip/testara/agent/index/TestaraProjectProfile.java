package io.github.ygrip.testara.agent.index;

import io.github.ygrip.testara.agent.catalog.RuntimeCatalogEntry;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record TestaraProjectProfile(
    Path projectRoot,
    BuildTool buildTool,
    String javaVersion,
    List<String> mavenModules,
    List<Path> featureRoots,
    List<Path> requestSpecRoots,
    List<Path> validationRoots,
    List<FeatureIndex> features,
    List<StepDefinitionIndex> stepDefinitions,
    List<CommandIndex> commands,
    List<ValidationIndex> validations,
    List<DriverIndex> drivers,
    List<TagIndex> tags,
    Map<String, String> properties,
    Map<String, Object> conventions,
    List<FlavorEntry> flavorSteps,
    List<RuntimeCatalogEntry> runtimeCatalog
) {
  /** Returns catalog entries for a given slice. */
  public List<RuntimeCatalogEntry> catalogForSlice(String slice) {
    return runtimeCatalog.stream()
        .filter(e -> e.slice().equalsIgnoreCase(slice))
        .collect(Collectors.toList());
  }

  /** All known config prefixes from the runtime catalog. */
  public List<String> knownConfigPrefixes() {
    return runtimeCatalog.stream().map(RuntimeCatalogEntry::prefix).distinct().collect(Collectors.toList());
  }
  /** Returns all flavor steps for a given slice (api, ui, sql, mongo, kafka, elastic, core). */
  public List<FlavorEntry> flavorStepsForSlice(String slice) {
    return flavorSteps.stream()
        .filter(e -> e.slice().equalsIgnoreCase(slice))
        .collect(Collectors.toList());
  }

  /** Finds the best-matching flavor step for a given keyword and intent text. */
  public java.util.Optional<FlavorEntry> findFlavorStep(String slice, String keyword, String intentText) {
    String lower = intentText.toLowerCase();
    return flavorSteps.stream()
        .filter(e -> e.slice().equalsIgnoreCase(slice) && e.keyword().equalsIgnoreCase(keyword))
        .filter(e -> e.matchesIntent(lower))
        .findFirst();
  }
  public int totalScenarios() {
    return features().stream().mapToInt(f -> f.scenarios().size()).sum();
  }

  public long totalExampleRows() {
    return features().stream().mapToLong(FeatureIndex::totalExampleRows).sum();
  }

  public long totalSteps() {
    return features().stream()
        .flatMap(f -> f.scenarios().stream())
        .flatMap(s -> s.steps().stream())
        .count();
  }

  public Map<String, Long> tagDistribution() {
    return tags().stream().collect(
        java.util.stream.Collectors.toMap(TagIndex::tag, t -> (long) t.scenarioCount()));
  }
}
