package io.github.ygrip.testara.agent.knowledge;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.ygrip.testara.agent.catalog.RuntimeCatalogEntry;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Serializes and restores TestaraProjectProfile to/from a JSON cache file.
 * Uses Jackson (already a dependency). Avoids re-indexing the entire project on every startup.
 */
final class ProfileSerializer {

  private static final Logger LOG = Logger.getLogger(ProfileSerializer.class.getName());
  static final int CACHE_VERSION = 4; // bump to invalidate all caches on schema change

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  /** Write profile to cache file. Silently skips on error. */
  static void save(Path cacheFile, TestaraProjectProfile profile) {
    try {
      Files.createDirectories(cacheFile.getParent());
      CacheEnvelope envelope = new CacheEnvelope(CACHE_VERSION, toDto(profile));
      Files.writeString(cacheFile, MAPPER.writeValueAsString(envelope), StandardCharsets.UTF_8);
      LOG.fine("Profile cached to " + cacheFile);
    } catch (Exception e) {
      LOG.fine("Cannot save profile cache: " + e.getMessage());
    }
  }

  /** Restore profile from cache file. Returns null if cache is missing, stale, or corrupt. */
  static TestaraProjectProfile load(Path cacheFile) {
    if (!Files.exists(cacheFile)) return null;
    try {
      String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
      CacheEnvelope envelope = MAPPER.readValue(json, CacheEnvelope.class);
      if (envelope.version() != CACHE_VERSION) {
        LOG.fine("Cache version mismatch — invalidating");
        return null;
      }
      // cacheFile = <projectRoot>/.testara-agent/knowledge/profile-cache.json -> 3 parents up is projectRoot
      return fromDto(envelope.profile(), cacheFile.getParent().getParent().getParent());
    } catch (Exception e) {
      LOG.fine("Cannot restore profile cache: " + e.getMessage());
      return null;
    }
  }

  // ── DTO wrappers (records can't be deserialized by Jackson without config) ──

  record CacheEnvelope(int version, ProfileDto profile) {
    CacheEnvelope() { this(0, null); } // Jackson no-arg
  }

  record ProfileDto(
      String buildTool, String javaVersion, List<String> modules,
      List<String> featureRoots, List<String> requestSpecRoots, List<String> validationRoots,
      List<FeatureDto> features, List<DriverDto> drivers, List<TagDto> tags,
      List<CommandDto> commands, List<ValidationDto> validations,
      List<StepDefDto> stepDefs, List<FlavorDto> flavorSteps,
      List<CatalogDto> runtimeCatalog, Map<String, String> properties
  ) {
    ProfileDto() {
      this(null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
          List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }
  }

  record CommandDto(String command, List<String> aliases, String returnType, boolean cacheable, String sourcePath, String className) {
    CommandDto() { this(null, List.of(), null, false, null, null); }
  }
  record ValidationDto(String validation, List<String> aliases, String actualType, String expectedType, boolean cacheable, String sourcePath, String className) {
    ValidationDto() { this(null, List.of(), null, null, false, null, null); }
  }
  record StepDefDto(String keyword, String expression, String sourcePath, String className) {
    StepDefDto() { this(null, null, null, null); }
  }
  record FlavorDto(String slice, String keyword, String expression, String example, String capability, String module, String className) {
    FlavorDto() { this(null, null, null, null, null, null, null); }
  }
  record CatalogDto(String slice, String prefix, String module, String className, List<String> exampleKeys) {
    CatalogDto() { this(null, null, null, null, List.of()); }
  }
  record FeatureDto(String path, String featureName, List<String> tags, List<ScenarioDto> scenarios, List<StepDto> backgroundSteps) {
    FeatureDto() { this(null, null, List.of(), List.of(), List.of()); }
  }
  record ScenarioDto(String name, String type, List<String> tags, List<StepDto> steps, List<ExamplesDto> examples) {
    ScenarioDto() { this(null, null, List.of(), List.of(), List.of()); }
  }
  record StepDto(String keyword, String text, List<List<String>> dataTable) {
    StepDto() { this(null, null, List.of()); }
  }
  record ExamplesDto(List<String> headers, int rowCount) {
    ExamplesDto() { this(List.of(), 0); }
  }
  record TagDto(String tag, int featureCount, int scenarioCount, List<String> featurePaths, List<String> scenarioNames) {
    TagDto() { this(null, 0, 0, List.of(), List.of()); }
  }
  record DriverDto(String name, String engineClass, List<String> platforms, String browserName, String sourcePath, String className) {
    DriverDto() { this(null, null, List.of(), null, null, null); }
  }

  // ── Conversion ────────────────────────────────────────────────────────────

  private static ProfileDto toDto(TestaraProjectProfile p) {
    return new ProfileDto(
        buildToolName(p.buildTool()),
        p.javaVersion(), p.mavenModules(),
        pathStrings(p.featureRoots()), pathStrings(p.requestSpecRoots()), pathStrings(p.validationRoots()),
        p.features().stream().map(ProfileSerializer::toFeatureDto).toList(),
        p.drivers().stream().map(dr -> new DriverDto(dr.name(), dr.engineClass(), dr.platforms(),
            dr.browserName(), pathStr(dr.sourcePath()), dr.className())).toList(),
        p.tags().stream().map(t -> new TagDto(t.tag(), t.featureCount(), t.scenarioCount(),
            pathStrings(t.featurePaths()), t.scenarioNames())).toList(),
        p.commands().stream().map(c -> new CommandDto(c.command(), c.aliases(), c.returnType(), c.cacheable(),
            pathStr(c.sourcePath()), c.className())).toList(),
        p.validations().stream().map(v -> new ValidationDto(v.validation(), v.aliases(), v.actualType(), v.expectedType(),
            v.cacheable(), pathStr(v.sourcePath()), v.className())).toList(),
        p.stepDefinitions().stream().map(s -> new StepDefDto(s.annotation(), s.expression(),
            pathStr(s.sourcePath()), s.className())).toList(),
        p.flavorSteps().stream().map(f -> new FlavorDto(f.slice(), f.keyword(), f.expression(),
            f.example(), f.capability(), f.module(), f.className())).toList(),
        p.runtimeCatalog().stream().map(r -> new CatalogDto(r.slice(), r.prefix(), r.module(),
            r.className(), r.exampleKeys())).toList(),
        p.properties()
    );
  }

  private static FeatureDto toFeatureDto(FeatureIndex f) {
    return new FeatureDto(pathStr(f.path()), f.featureName(), f.tags(),
        f.scenarios().stream().map(ProfileSerializer::toScenarioDto).toList(),
        f.backgroundSteps().stream().map(ProfileSerializer::toStepDto).toList());
  }

  private static ScenarioDto toScenarioDto(ScenarioIndex s) {
    return new ScenarioDto(s.name(), s.type() != null ? s.type().name() : null, s.tags(),
        s.steps().stream().map(ProfileSerializer::toStepDto).toList(),
        s.examples().stream().map(e -> new ExamplesDto(e.headers(), e.rowCount())).toList());
  }

  private static StepDto toStepDto(StepIndex st) {
    return new StepDto(st.keyword(), st.text(), st.dataTable());
  }

  private static TestaraProjectProfile fromDto(ProfileDto d, Path projectRoot) {
    BuildTool buildTool = null;
    try { if (d.buildTool() != null) buildTool = BuildTool.valueOf(d.buildTool()); } catch (Exception ignored) {}
    final BuildTool bt = buildTool;

    List<CommandIndex> commands = d.commands().stream().map(c ->
        new CommandIndex(c.command(), c.aliases(), c.returnType(), c.cacheable(),
            toPath(c.sourcePath()), c.className())).toList();
    List<ValidationIndex> validations = d.validations().stream().map(v ->
        new ValidationIndex(v.validation(), v.aliases(), v.actualType(), v.expectedType(),
            v.cacheable(), toPath(v.sourcePath()), v.className())).toList();
    List<StepDefinitionIndex> stepDefs = d.stepDefs().stream().map(s ->
        new StepDefinitionIndex(s.keyword(), s.expression(),
            toPath(s.sourcePath()), s.className(), "")).toList();
    // Note: 'keyword' field in StepDefDto stores the annotation value (Given/When/Then)
    List<FlavorEntry> flavorSteps = d.flavorSteps().stream().map(f ->
        new FlavorEntry(f.slice(), f.keyword(), f.expression(), f.example(), f.capability(), f.module(), f.className())).toList();
    List<RuntimeCatalogEntry> catalog = d.runtimeCatalog().stream().map(r ->
        new RuntimeCatalogEntry(r.slice(), r.prefix(), r.module(), r.className(), r.exampleKeys())).toList();

    List<FeatureIndex> features = d.features().stream().map(ProfileSerializer::fromFeatureDto).toList();
    List<DriverIndex> drivers = d.drivers().stream().map(dr ->
        new DriverIndex(dr.name(), dr.engineClass(), dr.platforms(), dr.browserName(),
            toPath(dr.sourcePath()), dr.className())).toList();
    List<TagIndex> tags = d.tags().stream().map(t ->
        new TagIndex(t.tag(), t.featureCount(), t.scenarioCount(), toPaths(t.featurePaths()), t.scenarioNames())).toList();

    return new TestaraProjectProfile(
        projectRoot, bt, d.javaVersion(), d.modules(),
        toPaths(d.featureRoots()), toPaths(d.requestSpecRoots()), toPaths(d.validationRoots()),
        features, stepDefs, commands, validations, drivers, tags,
        d.properties() != null ? d.properties() : Map.of(), Map.of(),
        flavorSteps, catalog);
  }

  private static FeatureIndex fromFeatureDto(FeatureDto f) {
    return new FeatureIndex(toPath(f.path()), f.featureName(), f.tags(),
        f.scenarios().stream().map(ProfileSerializer::fromScenarioDto).toList(),
        f.backgroundSteps().stream().map(ProfileSerializer::fromStepDto).toList());
  }

  private static ScenarioIndex fromScenarioDto(ScenarioDto s) {
    ScenarioType type = null;
    try { if (s.type() != null) type = ScenarioType.valueOf(s.type()); } catch (Exception ignored) {}
    return new ScenarioIndex(s.name(), type, s.tags(),
        s.steps().stream().map(ProfileSerializer::fromStepDto).toList(),
        s.examples().stream().map(e -> new ExamplesIndex(e.headers(), e.rowCount())).toList());
  }

  private static StepIndex fromStepDto(StepDto st) {
    return new StepIndex(st.keyword(), st.text(), st.dataTable());
  }

  private static String buildToolName(BuildTool bt) {
    if (bt == null) return null;
    return bt.name();
  }

  private static String pathStr(Path p) {
    if (p == null) return null;
    return p.toString();
  }

  private static Path toPath(String s) {
    if (s == null) return null;
    return Path.of(s);
  }

  private static List<String> pathStrings(List<Path> paths) {
    if (paths == null) return List.of();
    return paths.stream().map(Path::toString).toList();
  }

  private static List<Path> toPaths(List<String> paths) {
    if (paths == null) return List.of();
    return paths.stream().map(Path::of).toList();
  }

  private ProfileSerializer() {}
}
