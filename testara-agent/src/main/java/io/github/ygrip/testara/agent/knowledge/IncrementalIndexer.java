package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.ProjectIndexer;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Fingerprint-aware incremental indexer.
 *
 * <p>Compares current file fingerprints against the previous snapshot to decide:
 * <ul>
 *   <li>Cache reuse — no files changed</li>
 *   <li>Partial reindex — only feature/step files changed</li>
 *   <li>Full reindex — build config, scan locations, or structural change</li>
 * </ul>
 */
public final class IncrementalIndexer {

  private static final Logger LOG = Logger.getLogger(IncrementalIndexer.class.getName());
  private final ProjectIndexer indexer = new ProjectIndexer();

  public ProjectKnowledgeSnapshot index(Path projectRoot,
      ProjectKnowledgeSnapshot previous,
      ProjectFingerprint currentFingerprint) {

    // No previous snapshot — full index
    if (previous == null) {
      return fullReindex(projectRoot, currentFingerprint);
    }

    // Fingerprints match — reuse cache
    if (currentFingerprint.equals(previous.fingerprint())) {
      LOG.fine("Knowledge cache is fresh — reusing snapshot");
      return previous;
    }

    // Build config or structural change — full reindex
    if (requiresFullReindex(previous.fingerprint(), currentFingerprint)) {
      LOG.info("Structural change detected — full reindex");
      return fullReindex(projectRoot, currentFingerprint);
    }

    // Partial reindex (feature/step files changed only)
    LOG.info("Incremental change detected — partial reindex");
    return fullReindex(projectRoot, currentFingerprint); // simplified: full reindex
  }

  private ProjectKnowledgeSnapshot fullReindex(Path projectRoot,
      ProjectFingerprint fp) {
    TestaraProjectProfile profile = indexer.index(projectRoot);
    Instant now = Instant.now();

    var stats = KnowledgeStats.from(
        profile.features().size(), profile.totalScenarios(),
        profile.stepDefinitions().size(), profile.commands().size(),
        profile.validations().size(), profile.tags().size(),
        fp.fingerprints().size(), KnowledgeStatus.FRESH);

    return new ProjectKnowledgeSnapshot(1, now, now, fp, profile, stats);
  }

  private boolean requiresFullReindex(ProjectFingerprint oldFp,
      ProjectFingerprint newFp) {
    // If any BUILD or CONFIG file changed, full reindex
    return oldFp.fingerprints().entrySet().stream().anyMatch(e -> {
      var old = e.getValue();
      var neu = newFp.fingerprints().get(e.getKey());
      if (neu == null) return true; // file removed
      if (old.type() == FileType.BUILD || old.type() == FileType.CONFIG) {
        return old.size() != neu.size()
            || old.lastModifiedMillis() != neu.lastModifiedMillis();
      }
      return false;
    });
  }
}
