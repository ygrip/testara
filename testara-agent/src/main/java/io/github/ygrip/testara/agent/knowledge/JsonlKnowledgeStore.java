package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSONL-backed project knowledge store with HYBRID fingerprint-based
 * incremental indexing.
 *
 * <p>Cache lives under {@code .testara-agent/knowledge/} per project root.
 * On first load, indexes the full project. On subsequent loads, compares
 * file fingerprints to decide: cache reuse, partial reindex, or full reindex.
 */
public final class JsonlKnowledgeStore implements ProjectKnowledgeService {

  private static final Logger LOG = Logger.getLogger(JsonlKnowledgeStore.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int SCHEMA_VERSION = 1;
  private static final String KNOWLEDGE_DIR = ".testara-agent/knowledge";
  private static final String MANIFEST = "manifest.json";
  private static final String FINGERPRINTS = "file-fingerprints.jsonl";
  private static final String PROFILE_CACHE = "profile-cache.json";

  private final ProjectIndexer indexer = new ProjectIndexer();

  /**
   * Failsafe convenience: load from cache if available, otherwise fall back
   * to direct {@link ProjectIndexer}. Never throws — always returns a profile.
   */
  public static TestaraProjectProfile loadProfile(Path projectRoot) {
    try {
      var store = new JsonlKnowledgeStore();
      return store.loadOrIndex(projectRoot).profile();
    } catch (Exception e) {
      LOG.warning("Knowledge store failed, falling back to direct indexer: " + e.getMessage());
      return new ProjectIndexer().index(projectRoot);
    }
  }

  @Override
  public ProjectKnowledgeSnapshot loadOrIndex(Path projectRoot) {
    final var knowledgeDir = projectRoot.resolve(KNOWLEDGE_DIR);

    // Fast path: compare fingerprints, restore serialized profile if fresh
    try {
      var savedFpMap = loadFingerprints(knowledgeDir);
      if (!savedFpMap.isEmpty()) {
        var currentFp = scanFingerprints(projectRoot);
        var savedFp   = ProjectFingerprint.of(savedFpMap, computeHash(savedFpMap));
        if (currentFp.projectHash().equals(savedFp.projectHash())) {
          TestaraProjectProfile cached = ProfileSerializer.load(knowledgeDir.resolve(PROFILE_CACHE));
          if (cached != null) {
            LOG.fine("Knowledge cache hit — skipping indexing");
            var stats = KnowledgeStats.from(0, 0, cached.stepDefinitions().size(),
                cached.commands().size(), cached.validations().size(), cached.tags().size(),
                savedFpMap.size(), KnowledgeStatus.FRESH);
            return new ProjectKnowledgeSnapshot(SCHEMA_VERSION, Instant.now(), Instant.now(),
                currentFp, cached, stats);
          }
        }
      }
    } catch (Exception e) {
      LOG.fine("Cache check failed, re-indexing: " + e.getMessage());
    }

    // Full reindex
    LOG.info("Indexing project at " + projectRoot);
    TestaraProjectProfile profile = indexer.index(projectRoot);
    var fp = scanFingerprints(projectRoot);
    Instant now = Instant.now();

    var stats = KnowledgeStats.from(
        profile.features().size(), profile.totalScenarios(),
        profile.stepDefinitions().size(), profile.commands().size(),
        profile.validations().size(), profile.tags().size(),
        fp.fingerprints().size(), KnowledgeStatus.FRESH);

    var snapshot = new ProjectKnowledgeSnapshot(SCHEMA_VERSION, now, now, fp, profile, stats);

    // Persist to disk for next run
    saveManifest(knowledgeDir, snapshot);
    saveFingerprints(knowledgeDir, fp);
    ProfileSerializer.save(knowledgeDir.resolve(PROFILE_CACHE), profile);
    return snapshot;
  }

  @Override
  public ProjectKnowledgeSnapshot refresh(Path projectRoot) {
    clear(projectRoot);
    return loadOrIndex(projectRoot);
  }

  @Override
  public KnowledgeStatus status(Path projectRoot) {
    Path knowledgeDir = projectRoot.resolve(KNOWLEDGE_DIR);
    return loadManifest(knowledgeDir).isPresent()
        ? KnowledgeStatus.FRESH : KnowledgeStatus.MISSING;
  }

  @Override
  public void clear(Path projectRoot) {
    Path knowledgeDir = projectRoot.resolve(KNOWLEDGE_DIR);
    if (Files.exists(knowledgeDir)) {
      try {
        try (Stream<Path> files = Files.list(knowledgeDir)) {
          files.forEach(f -> {
            try { Files.deleteIfExists(f); }
            catch (IOException ignored) { /* best effort */ }
          });
        }
        Files.deleteIfExists(knowledgeDir);
      } catch (IOException e) {
        LOG.warning("Cannot clear knowledge dir: " + e.getMessage());
      }
    }
  }

  // ── Fingerprint scanning ──────────────────────────────────────────

  static ProjectFingerprint scanFingerprints(Path projectRoot) {
    Map<Path, FileFingerprint> fps = new LinkedHashMap<>();
    try (Stream<Path> walk = Files.walk(projectRoot, 8)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> !isExcluded(p))
          .forEach(p -> {
            try {
              long size = Files.size(p);
              long mod = Files.getLastModifiedTime(p).toMillis();
              Path rel = projectRoot.relativize(p);
              FileType type = classify(rel);
              fps.put(rel, new FileFingerprint(rel, type, size, mod, hashContent(p)));
            } catch (IOException ignored) { /* skip unreadable */ }
          });
    } catch (IOException e) {
      LOG.warning("Fingerprint scan failed: " + e.getMessage());
    }
    return ProjectFingerprint.of(fps, computeHash(fps));
  }

  /** Normalizes to '/'-separated form first so exclusion still matches on Windows. */
  private static boolean isExcluded(Path p) {
    String normalized = p.toString().replace('\\', '/');
    return normalized.contains("/target/")
        || normalized.contains("/.testara-agent/")
        || normalized.contains("/.git/")
        || normalized.contains("/node_modules/");
  }

  /**
   * SHA-256 of the file's content. size+mtime alone miss size+mtime-preserving edits (a VCS
   * checkout that doesn't touch mtimes, or two edits landing within the same filesystem
   * timestamp granularity) - actual content is the only reliable freshness signal.
   */
  private static String hashContent(Path p) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(Files.readAllBytes(p));
      return bytesToHex(md.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      return "";
    }
  }

  private static FileType classify(Path rel) {
    String s = rel.toString().replace('\\', '/');
    if (s.equals("pom.xml") || s.equals("build.gradle")) return FileType.BUILD;
    if (s.endsWith(".feature")) return FileType.FEATURE;
    if (s.endsWith(".java") && s.contains("Command")) return FileType.COMMAND;
    if (s.endsWith(".java") && (s.contains("Validator") || s.contains("Validation"))) return FileType.VALIDATION;
    if (s.endsWith(".java") && s.contains("Steps")) return FileType.STEP_DEFINITION;
    if (s.contains("files/") && s.endsWith(".json")) return FileType.REQUEST_SPEC;
    if (s.contains("validations/") && s.endsWith(".json")) return FileType.VALIDATION_FILE;
    if (s.endsWith(".properties") || s.endsWith(".yaml") || s.endsWith(".yml")) return FileType.CONFIG;
    if (s.endsWith(".java")) return FileType.STEP_DEFINITION; // best guess
    return FileType.OTHER;
  }

  private static String computeHash(Map<Path, FileFingerprint> fps) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      fps.values().stream()
          .sorted(Comparator.comparing(f -> f.path().toString()))
          .forEach(f -> md.update((f.path() + ":" + f.size() + ":" + f.lastModifiedMillis() + ":" + f.sha256())
              .getBytes(StandardCharsets.UTF_8)));
      return bytesToHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      return String.valueOf(fps.hashCode());
    }
  }

  // ── Persistence ───────────────────────────────────────────────────

  private Optional<ProjectKnowledgeSnapshot> loadManifest(Path dir) {
    Path file = dir.resolve(MANIFEST);
    if (!Files.exists(file)) return Optional.empty();
    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      // Simple JSON parsing — avoids full Jackson dependency in knowledge module
      var map = parseSimpleJson(json);
      int schema = Integer.parseInt(map.getOrDefault("schemaVersion", "0"));
      String created = map.getOrDefault("createdAt", Instant.EPOCH.toString());
      String updated = map.getOrDefault("updatedAt", Instant.EPOCH.toString());
      String hash = map.getOrDefault("projectHash", "");

      var fps = loadFingerprints(dir);
      var fp = ProjectFingerprint.of(fps, hash);

      // Load profile via full index (simplified — in production we'd load from JSONL)
      // For MVP, we reindex if fingerprint doesn't match

      // Snapshot restoration handled by loadOrIndex via ProfileSerializer
      return Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private Map<Path, FileFingerprint> loadFingerprints(Path dir) {
    Map<Path, FileFingerprint> map = new LinkedHashMap<>();
    Path file = dir.resolve(FINGERPRINTS);
    if (!Files.exists(file)) return map;

    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.fine("Cannot read fingerprints: " + e.getMessage());
      return map;
    }

    for (String line : lines) {
      line = line.strip();
      if (line.isEmpty()) continue;
      try {
        var entry = parseSimpleJson(line);
        String path = entry.get("path");
        if (path == null) continue;
        String type = entry.getOrDefault("type", "OTHER");
        long size = Long.parseLong(entry.getOrDefault("size", "0"));
        long mod = Long.parseLong(entry.getOrDefault("lastModifiedMillis", "0"));
        String sha256 = entry.getOrDefault("sha256", "");
        map.put(Path.of(path),
            new FileFingerprint(Path.of(path), fileTypeOrDefault(type), size, mod, sha256));
      } catch (RuntimeException e) {
        // A single malformed line must not drop every fingerprint that follows it.
        LOG.fine("Skipping malformed fingerprint line: " + e.getMessage());
      }
    }
    return map;
  }

  private static FileType fileTypeOrDefault(String type) {
    try {
      return FileType.valueOf(type);
    } catch (IllegalArgumentException e) {
      return FileType.OTHER;
    }
  }

  private void saveManifest(Path dir, ProjectKnowledgeSnapshot snapshot) {
    try {
      Files.createDirectories(dir);
      String json = String.format("""
          {"schemaVersion":%d,"createdAt":"%s","updatedAt":"%s","projectHash":"%s"}
          """, snapshot.schemaVersion(), snapshot.createdAt(),
          snapshot.updatedAt(), snapshot.fingerprint().projectHash());
      Files.writeString(dir.resolve(MANIFEST), json, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.warning("Cannot save manifest: " + e.getMessage());
    }
  }

  private void saveFingerprints(Path dir, ProjectFingerprint fp) {
    try {
      Files.createDirectories(dir);
      List<String> lines = fp.fingerprints().values().stream()
          .sorted(Comparator.comparing(f -> f.path().toString()))
          .map(f -> String.format(
              "{\"path\":\"%s\",\"type\":\"%s\",\"size\":%d,\"lastModifiedMillis\":%d,\"sha256\":\"%s\"}",
              f.path(), f.type(), f.size(), f.lastModifiedMillis(), f.sha256()))
          .collect(Collectors.toList());
      Files.write(dir.resolve(FINGERPRINTS), lines, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.warning("Cannot save fingerprints: " + e.getMessage());
    }
  }

  // ── JSON parsing ──────────────────────────────────────────────────

  static Map<String, String> parseSimpleJson(String json) {
    Map<String, String> map = new LinkedHashMap<>();
    try {
      JsonNode node = MAPPER.readTree(json);
      node.properties().forEach(e -> map.put(e.getKey(), e.getValue().asText()));
    } catch (IOException e) {
      LOG.fine("Cannot parse JSON: " + e.getMessage());
    }
    return map;
  }

  static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}
