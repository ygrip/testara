package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import java.time.Instant;

/**
 * Immutable snapshot of a project's indexed knowledge at a point in time.
 */
public record ProjectKnowledgeSnapshot(
    int schemaVersion,
    Instant createdAt,
    Instant updatedAt,
    ProjectFingerprint fingerprint,
    TestaraProjectProfile profile,
    KnowledgeStats stats
) {
  public boolean isFresh() {
    return stats != null && stats.status() == KnowledgeStatus.FRESH;
  }
}
