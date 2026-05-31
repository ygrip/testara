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
  static final int CACHE_VERSION = 3; // bump to invalidate all caches on schema change

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
      return fromDto(envelope.profile(), cacheFile.getParent().getParent().getParent()); // 3 levels up from .testara-agent/knowledge/
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
      List<CommandDto> commands, List<ValidationDto> validations,
      List<StepDefDto> stepDefs, List<FlavorDto> flavorSteps,
      List<CatalogDto> runtimeCatalog, Map<String, String> properties
  ) {
    ProfileDto() { this(null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()); }
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

  // ── Conversion ────────────────────────────────────────────────────────────

  private static ProfileDto toDto(TestaraProjectProfile p) {
    return new ProfileDto(
        p.buildTool() != null ? p.buildTool().name() : null,
        p.javaVersion(), p.mavenModules(),
        p.commands().stream().map(c -> new CommandDto(c.command(), c.aliases(), c.returnType(), c.cacheable(),
            c.sourcePath() != null ? c.sourcePath().toString() : null, c.className())).toList(),
        p.validations().stream().map(v -> new ValidationDto(v.validation(), v.aliases(), v.actualType(), v.expectedType(),
            v.cacheable(), v.sourcePath() != null ? v.sourcePath().toString() : null, v.className())).toList(),
        p.stepDefinitions().stream().map(s -> new StepDefDto(s.annotation(), s.expression(),
            s.sourcePath() != null ? s.sourcePath().toString() : null, s.className())).toList(),
        p.flavorSteps().stream().map(f -> new FlavorDto(f.slice(), f.keyword(), f.expression(),
            f.example(), f.capability(), f.module(), f.className())).toList(),
        p.runtimeCatalog().stream().map(r -> new CatalogDto(r.slice(), r.prefix(), r.module(),
            r.className(), r.exampleKeys())).toList(),
        p.properties()
    );
  }

  private static TestaraProjectProfile fromDto(ProfileDto d, Path knowledgeDir) {
    // Reconstruct projectRoot: .testara-agent/knowledge -> project root (2 levels up)
    Path projectRoot = knowledgeDir.getParent().getParent();
    BuildTool buildTool = null;
    try { if (d.buildTool() != null) buildTool = BuildTool.valueOf(d.buildTool()); } catch (Exception ignored) {}
    final BuildTool bt = buildTool;

    List<CommandIndex> commands = d.commands().stream().map(c ->
        new CommandIndex(c.command(), c.aliases(), c.returnType(), c.cacheable(),
            c.sourcePath() != null ? Path.of(c.sourcePath()) : null, c.className())).toList();
    List<ValidationIndex> validations = d.validations().stream().map(v ->
        new ValidationIndex(v.validation(), v.aliases(), v.actualType(), v.expectedType(),
            v.cacheable(), v.sourcePath() != null ? Path.of(v.sourcePath()) : null, v.className())).toList();
    List<StepDefinitionIndex> stepDefs = d.stepDefs().stream().map(s ->
        new StepDefinitionIndex(s.keyword(), s.expression(),
            s.sourcePath() != null ? Path.of(s.sourcePath()) : null, s.className(), "")).toList();
    // Note: 'keyword' field in StepDefDto stores the annotation value (Given/When/Then)
    List<FlavorEntry> flavorSteps = d.flavorSteps().stream().map(f ->
        new FlavorEntry(f.slice(), f.keyword(), f.expression(), f.example(), f.capability(), f.module(), f.className())).toList();
    List<RuntimeCatalogEntry> catalog = d.runtimeCatalog().stream().map(r ->
        new RuntimeCatalogEntry(r.slice(), r.prefix(), r.module(), r.className(), r.exampleKeys())).toList();

    return new TestaraProjectProfile(
        projectRoot, bt, d.javaVersion(), d.modules(),
        List.of(), List.of(), List.of(),
        List.of(), stepDefs, commands, validations, List.of(), List.of(),
        d.properties() != null ? d.properties() : Map.of(), Map.of(),
        flavorSteps, catalog);
  }

  private ProfileSerializer() {}
}
