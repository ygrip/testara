package io.github.ygrip.testara.agent.knowledge;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record ProjectFingerprint(
    Map<Path, FileFingerprint> fingerprints,
    String projectHash
) {
  public static ProjectFingerprint of(Map<Path, FileFingerprint> fps, String hash) {
    return new ProjectFingerprint(Map.copyOf(fps), hash);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ProjectFingerprint that)) return false;
    return Objects.equals(projectHash, that.projectHash)
        && Objects.equals(fingerprints, that.fingerprints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fingerprints, projectHash);
  }
}
