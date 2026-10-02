package io.github.ygrip.testara.agent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cache freshness, persistence format and write-order guarantees of {@link JsonlKnowledgeStore}. */
class JsonlKnowledgeStoreCacheTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String FEATURE = "Feature: F\n  Scenario: one\n    Given a step\n";

  @Test
  void fingerprintsProjectThatLivesUnderABuildDirectory(@TempDir Path parent) throws IOException {
    Path root = parent.resolve("build/workspace/project");
    writeFeature(root, FEATURE);

    assertFalse(JsonlKnowledgeStore.scanFingerprints(root).fingerprints().isEmpty(),
        "exclusions must apply below the project root only");
    JsonlKnowledgeStore store = new JsonlKnowledgeStore();
    store.loadOrIndex(root);
    assertEquals(KnowledgeStatus.FRESH, store.status(root));
  }

  @Test
  void writesFingerprintsAsValidJsonWithPortablePaths(@TempDir Path root) throws IOException {
    writeFeature(root, FEATURE);
    Files.writeString(root.resolve("notes \"quoted\".txt"), "x");

    JsonlKnowledgeStore store = new JsonlKnowledgeStore();
    store.loadOrIndex(root);

    List<String> lines = Files.readAllLines(root.resolve(".testara-agent/knowledge/file-fingerprints.jsonl"));
    assertFalse(lines.isEmpty());
    for (String line : lines) {
      JsonNode node = MAPPER.readTree(line);
      assertFalse(node.path("path").asText().contains("\\"), line);
    }
    assertEquals(KnowledgeStatus.FRESH, store.status(root), "a quote in a file name must not make the cache stale");
  }

  @Test
  void failedProfileWriteNeverServesTheOldProfileAsFresh(@TempDir Path root) throws IOException {
    writeFeature(root, FEATURE);
    JsonlKnowledgeStore store = new JsonlKnowledgeStore();
    store.loadOrIndex(root);

    // Block the next profile write: a non-empty directory cannot be replaced by the cache file.
    Path profileCache = root.resolve(".testara-agent/knowledge/profile-cache.json");
    Files.delete(profileCache);
    Files.createDirectories(profileCache.resolve("blocker"));
    writeFeature(root, FEATURE + "  Scenario: two\n    Given a step\n");

    assertEquals(2, store.loadOrIndex(root).profile().totalScenarios());
    assertEquals(KnowledgeStatus.MISSING, store.status(root),
        "fingerprints must not be committed when the profile could not be written");
    assertEquals(2, store.loadOrIndex(root).profile().totalScenarios());
  }

  @Test
  void statusIsStaleWhenCacheOrSchemaVersionChanges(@TempDir Path root) throws IOException {
    writeFeature(root, FEATURE);
    JsonlKnowledgeStore store = new JsonlKnowledgeStore();
    store.loadOrIndex(root);
    Path knowledge = root.resolve(".testara-agent/knowledge");

    rewriteIntField(knowledge.resolve("profile-cache.json"), "version", ProfileSerializer.CACHE_VERSION - 1);
    assertEquals(KnowledgeStatus.STALE, store.status(root));

    store.refresh(root);
    assertEquals(KnowledgeStatus.FRESH, store.status(root));
    rewriteIntField(knowledge.resolve("manifest.json"), "schemaVersion", 0);
    assertEquals(KnowledgeStatus.STALE, store.status(root));
  }

  @Test
  void fingerprintsModulesDeclaredOutsideTheProjectRoot(@TempDir Path parent) throws IOException {
    Path root = parent.resolve("project");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project><modules><module>../sibling</module></modules></project>");
    Path sibling = parent.resolve("sibling/src/main/java/com/acme/Steps.java");
    Files.createDirectories(sibling.getParent());
    Files.writeString(sibling, "package com.acme; public class Steps { }");

    JsonlKnowledgeStore store = new JsonlKnowledgeStore();
    store.loadOrIndex(root);
    assertEquals(KnowledgeStatus.FRESH, store.status(root));

    Files.writeString(sibling, "package com.acme; public class Steps { /* changed */ }");
    assertEquals(KnowledgeStatus.STALE, store.status(root));
  }

  private void writeFeature(Path root, String content) throws IOException {
    Path feature = root.resolve("src/test/resources/features/f.feature");
    Files.createDirectories(feature.getParent());
    Files.writeString(feature, content, StandardCharsets.UTF_8);
  }

  private void rewriteIntField(Path file, String field, int value) throws IOException {
    ObjectNode node = (ObjectNode) MAPPER.readTree(file.toFile());
    node.put(field, value);
    Files.writeString(file, MAPPER.writeValueAsString(node));
    assertTrue(Files.exists(file));
  }
}
