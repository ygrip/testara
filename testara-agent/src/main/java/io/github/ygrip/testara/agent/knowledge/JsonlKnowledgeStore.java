package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
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
    // Scan before indexing: a file edited while indexing makes the next load stale, never falsely fresh.
    ProjectFingerprint currentFp = scanFingerprints(projectRoot);

    // Fast path: compare fingerprints, restore serialized profile if fresh
    try {
      var savedFpMap = loadFingerprints(knowledgeDir);
      if (!savedFpMap.isEmpty() && hasCurrentSchema(knowledgeDir)
          && currentFp.projectHash().equals(computeHash(savedFpMap))) {
        TestaraProjectProfile cached = ProfileSerializer.load(knowledgeDir.resolve(PROFILE_CACHE));
        if (cached != null) {
          LOG.fine("Knowledge cache hit — skipping indexing");
          var stats = KnowledgeStats.from(cached.features().size(), cached.totalScenarios(),
              cached.stepDefinitions().size(), cached.commands().size(), cached.validations().size(),
              cached.tags().size(), savedFpMap.size(), KnowledgeStatus.FRESH);
          return new ProjectKnowledgeSnapshot(SCHEMA_VERSION, Instant.now(), Instant.now(),
              currentFp, cached, stats);
        }
      }
    } catch (RuntimeException e) {
      LOG.warning("Cache check failed, re-indexing: " + e.getMessage());
    }

    // Full reindex
    LOG.info("Indexing project at " + projectRoot);
    TestaraProjectProfile profile = indexer.index(projectRoot);
    Instant now = Instant.now();

    var stats = KnowledgeStats.from(
        profile.features().size(), profile.totalScenarios(),
        profile.stepDefinitions().size(), profile.commands().size(),
        profile.validations().size(), profile.tags().size(),
        currentFp.fingerprints().size(), KnowledgeStatus.FRESH);

    var snapshot = new ProjectKnowledgeSnapshot(SCHEMA_VERSION, now, now, currentFp, profile, stats);
    persist(knowledgeDir, snapshot);
    return snapshot;
  }

  /**
   * Writes the profile first and the fingerprints last: the fingerprints file is what marks the cache
   * valid, so a failed profile write can never leave an old profile looking fresh.
   */
  private void persist(Path knowledgeDir, ProjectKnowledgeSnapshot snapshot) {
    try {
      Files.deleteIfExists(knowledgeDir.resolve(FINGERPRINTS));
      ProfileSerializer.save(knowledgeDir.resolve(PROFILE_CACHE), snapshot.profile());
      writeAtomically(knowledgeDir.resolve(MANIFEST), manifestJson(snapshot));
      writeAtomically(knowledgeDir.resolve(FINGERPRINTS), fingerprintLines(snapshot.fingerprint()));
    } catch (IOException e) {
      LOG.warning("Cannot persist knowledge cache under " + knowledgeDir
          + " (the next load re-indexes): " + e.getMessage());
    }
  }

  @Override
  public ProjectKnowledgeSnapshot refresh(Path projectRoot) {
    clear(projectRoot);
    return loadOrIndex(projectRoot);
  }

  @Override
  public KnowledgeStatus status(Path projectRoot) {
    Path knowledgeDir = projectRoot.resolve(KNOWLEDGE_DIR);
    if (!Files.exists(knowledgeDir.resolve(MANIFEST))
        || !Files.exists(knowledgeDir.resolve(FINGERPRINTS))
        || !Files.exists(knowledgeDir.resolve(PROFILE_CACHE))) {
      return KnowledgeStatus.MISSING;
    }
    try {
      Map<Path, FileFingerprint> savedFpMap = loadFingerprints(knowledgeDir);
      if (savedFpMap.isEmpty()) return KnowledgeStatus.MISSING;
      if (!hasCurrentSchema(knowledgeDir)
          || !ProfileSerializer.hasCurrentVersion(knowledgeDir.resolve(PROFILE_CACHE))) {
        return KnowledgeStatus.STALE;
      }
      ProjectFingerprint current = scanFingerprints(projectRoot);
      String savedHash = computeHash(savedFpMap);
      return current.projectHash().equals(savedHash)
          ? KnowledgeStatus.FRESH : KnowledgeStatus.STALE;
    } catch (Exception e) {
      LOG.fine("Cannot determine knowledge status: " + e.getMessage());
      return KnowledgeStatus.STALE;
    }
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

  /**
   * Fingerprints every file the indexer reads: the project tree plus modules declared outside it
   * (e.g. {@code ../sibling}). Exclusions apply to directories below each root only, so a project
   * that itself lives under a {@code build/} or {@code target/} directory is still fingerprinted.
   */
  static ProjectFingerprint scanFingerprints(Path projectRoot) {
    Path base = projectRoot.toAbsolutePath().normalize();
    Map<Path, FileFingerprint> fps = new LinkedHashMap<>();
    for (Path root : ProjectIndexer.collectJavaSourceRoots(base, ProjectIndexer.detectModules(base))) {
      if (!Files.isDirectory(root)) continue;
      try {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root) && ProjectIndexer.isExcludedDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
            return FileVisitResult.CONTINUE;
          }
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
            Path rel = base.relativize(file);
            try {
              fps.put(rel, new FileFingerprint(rel, classify(rel), attrs.size(),
                  attrs.lastModifiedTime().toMillis(), hashContent(file)));
            } catch (IOException e) {
              LOG.fine("Skipping unreadable file " + file + ": " + e.getMessage());
            }
            return FileVisitResult.CONTINUE;
          }
          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            LOG.fine("Skipping inaccessible path " + file + ": " + exc.getMessage());
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        LOG.warning("Fingerprint scan failed under " + root + ": " + e.getMessage());
      }
    }
    return ProjectFingerprint.of(fps, computeHash(fps));
  }

  /**
   * SHA-256 of the file's content. size+mtime alone miss size+mtime-preserving edits (a VCS
   * checkout that doesn't touch mtimes, or two edits landing within the same filesystem
   * timestamp granularity) - actual content is the only reliable freshness signal.
   */
  private static String hashContent(Path p) throws IOException {
    MessageDigest md = sha256();
    try (var in = Files.newInputStream(p)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        md.update(buffer, 0, read);
      }
    }
    return bytesToHex(md.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java platform", e);
    }
  }

  /** '/'-separated form so cache files are identical (and valid JSON) on every OS. */
  private static String portablePath(Path p) {
    return p.toString().replace('\\', '/');
  }

  private static FileType classify(Path rel) {
    String s = portablePath(rel);
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
    MessageDigest md = sha256();
    fps.values().stream()
        .sorted(Comparator.comparing(f -> portablePath(f.path())))
        .forEach(f -> md.update((portablePath(f.path()) + ":" + f.size() + ":" + f.lastModifiedMillis()
            + ":" + f.sha256()).getBytes(StandardCharsets.UTF_8)));
    return bytesToHex(md.digest());
  }

  // ── Persistence ───────────────────────────────────────────────────

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

  private boolean hasCurrentSchema(Path dir) {
    Path manifest = dir.resolve(MANIFEST);
    if (!Files.exists(manifest)) return false;
    try {
      return MAPPER.readTree(manifest.toFile()).path("schemaVersion").asInt(-1) == SCHEMA_VERSION;
    } catch (IOException e) {
      LOG.fine("Cannot read manifest: " + e.getMessage());
      return false;
    }
  }

  private static String manifestJson(ProjectKnowledgeSnapshot snapshot) throws IOException {
    ObjectNode manifest = MAPPER.createObjectNode();
    manifest.put("schemaVersion", snapshot.schemaVersion());
    manifest.put("createdAt", snapshot.createdAt().toString());
    manifest.put("updatedAt", snapshot.updatedAt().toString());
    manifest.put("projectHash", snapshot.fingerprint().projectHash());
    return MAPPER.writeValueAsString(manifest) + "\n";
  }

  private static String fingerprintLines(ProjectFingerprint fp) throws IOException {
    StringBuilder lines = new StringBuilder();
    List<FileFingerprint> sorted = fp.fingerprints().values().stream()
        .sorted(Comparator.comparing(f -> portablePath(f.path())))
        .toList();
    for (FileFingerprint f : sorted) {
      ObjectNode line = MAPPER.createObjectNode();
      line.put("path", portablePath(f.path()));
      line.put("type", f.type().name());
      line.put("size", f.size());
      line.put("lastModifiedMillis", f.lastModifiedMillis());
      line.put("sha256", f.sha256());
      lines.append(MAPPER.writeValueAsString(line)).append('\n');
    }
    return lines.toString();
  }

  /** Writes via a sibling temp file and an atomic rename so readers never see a partial file. */
  static void writeAtomically(Path target, String content) throws IOException {
    Path dir = target.toAbsolutePath().getParent();
    Files.createDirectories(dir);
    Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
    try {
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        LOG.fine("Atomic move unsupported for " + target + ", replacing non-atomically");
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
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
