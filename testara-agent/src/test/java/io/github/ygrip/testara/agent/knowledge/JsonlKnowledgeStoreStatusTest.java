package io.github.ygrip.testara.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonlKnowledgeStoreStatusTest {

  @Test
  void statusTracksFreshAndStaleCacheAndCacheHitKeepsStats(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("pom.xml"),
        "<project><properties><java.version>21</java.version></properties></project>");
    Path feature = root.resolve("src/test/resources/features/status.feature");
    Files.createDirectories(feature.getParent());
    Files.writeString(feature, """
        @api
        Feature: Status
          Scenario: Cached scenario
            Given a precondition
            Then it works
        """);

    JsonlKnowledgeStore store = new JsonlKnowledgeStore();
    ProjectKnowledgeSnapshot first = store.loadOrIndex(root);

    assertEquals(1, first.stats().featureCount());
    assertEquals(1, first.stats().scenarioCount());
    assertEquals(KnowledgeStatus.FRESH, store.status(root));

    ProjectKnowledgeSnapshot cached = store.loadOrIndex(root);
    assertEquals(1, cached.stats().featureCount());
    assertEquals(1, cached.stats().scenarioCount());

    Files.writeString(feature, Files.readString(feature) + "\n# changed");
    assertEquals(KnowledgeStatus.STALE, store.status(root));
  }
}
