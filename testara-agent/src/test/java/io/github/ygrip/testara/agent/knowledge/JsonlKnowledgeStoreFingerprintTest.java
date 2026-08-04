package io.github.ygrip.testara.agent.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-all.5: fingerprints were path:size:mtime only, so a
 * size+mtime-preserving content edit (e.g. a VCS checkout that doesn't touch mtimes, or two edits
 * within the same filesystem timestamp granularity) was silently treated as unchanged.
 */
class JsonlKnowledgeStoreFingerprintTest {

  @Test
  void detectsContentChangeEvenWhenSizeAndMtimeAreUnchanged(@TempDir Path projectRoot) throws IOException {
    Path file = projectRoot.resolve("Notes.java");
    Files.writeString(file, "aaaaa", StandardCharsets.UTF_8); // 5 bytes

    ProjectFingerprint before = JsonlKnowledgeStore.scanFingerprints(projectRoot);
    FileTime originalMtime = Files.getLastModifiedTime(file);

    // Same size (5 bytes), forced back to the same mtime - only the content differs.
    Files.writeString(file, "bbbbb", StandardCharsets.UTF_8);
    Files.setLastModifiedTime(file, originalMtime);

    ProjectFingerprint after = JsonlKnowledgeStore.scanFingerprints(projectRoot);

    assertNotEquals(before.projectHash(), after.projectHash(),
        "a content change must change the project hash even when size and mtime are identical");
  }

  @Test
  void computesANonBlankContentHashPerFile(@TempDir Path projectRoot) throws IOException {
    Files.writeString(projectRoot.resolve("Notes.java"), "hello", StandardCharsets.UTF_8);

    ProjectFingerprint fp = JsonlKnowledgeStore.scanFingerprints(projectRoot);

    FileFingerprint entry = fp.fingerprints().get(Path.of("Notes.java"));
    assertNotNull(entry);
    assertTrue(entry.sha256() != null && !entry.sha256().isBlank());
  }
}
