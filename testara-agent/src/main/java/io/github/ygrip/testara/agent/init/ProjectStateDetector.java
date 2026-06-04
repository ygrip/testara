package io.github.ygrip.testara.agent.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Classifies a target directory before testara_init runs.
 */
public class ProjectStateDetector {

  public enum ProjectState {
    FRESH,
    MAVEN_PROJECT,
    TESTARA_PROJECT,
    UNSUPPORTED_GRADLE,
    AMBIGUOUS
  }

  public ProjectState detect(Path dir) {
    if (!Files.exists(dir) || isEmpty(dir)) return ProjectState.FRESH;

    boolean hasPom    = Files.exists(dir.resolve("pom.xml"));
    boolean hasGradle = Files.exists(dir.resolve("build.gradle"))
        || Files.exists(dir.resolve("build.gradle.kts"));

    if (hasPom && containsTestaraDependency(dir)) return ProjectState.TESTARA_PROJECT;
    if (hasPom) return ProjectState.MAVEN_PROJECT;
    if (hasGradle) return ProjectState.UNSUPPORTED_GRADLE;
    return ProjectState.AMBIGUOUS;
  }

  private boolean isEmpty(Path dir) {
    try (var stream = Files.list(dir)) {
      return stream.findFirst().isEmpty();
    } catch (IOException e) {
      return false;
    }
  }

  private boolean containsTestaraDependency(Path dir) {
    Path pom = dir.resolve("pom.xml");
    try {
      String content = Files.readString(pom);
      return content.contains("io.github.ygrip");
    } catch (IOException e) {
      return false;
    }
  }
}
