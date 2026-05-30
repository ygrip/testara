package io.github.ygrip.testara.agent.knowledge;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ProjectFingerprint(
    Map<Path, FileFingerprint> fingerprints,
    String projectHash
) {
  public static ProjectFingerprint of(Map<Path, FileFingerprint> fps, String hash) {
    return new ProjectFingerprint(Map.copyOf(fps), hash);
  }

  public boolean equals(ProjectFingerprint other) {
    if (other == null) return false;
    if (fingerprints.size() != other.fingerprints.size()) return false;
    return fingerprints.entrySet().stream()
        .allMatch(e -> {
          var otherFp = other.fingerprints.get(e.getKey());
          return otherFp != null
              && otherFp.size() == e.getValue().size()
              && otherFp.lastModifiedMillis() == e.getValue().lastModifiedMillis();
        });
  }
}
