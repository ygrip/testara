package io.github.ygrip.testara.agent.knowledge;

import java.nio.file.Path;

/**
 * Central entry point for accessing cached project knowledge.
 * Skills use this instead of calling {@code ProjectIndexer} directly.
 */
public interface ProjectKnowledgeService {

  /** Load cached snapshot, or index from scratch if missing/stale. */
  ProjectKnowledgeSnapshot loadOrIndex(Path projectRoot);

  /** Force re-index regardless of cache state. */
  ProjectKnowledgeSnapshot refresh(Path projectRoot);

  /** Check cache status without indexing. */
  KnowledgeStatus status(Path projectRoot);

  /** Clear all cached knowledge for this project. */
  void clear(Path projectRoot);
}
