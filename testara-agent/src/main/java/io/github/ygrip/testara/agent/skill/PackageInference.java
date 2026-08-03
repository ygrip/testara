package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Infers a Java base package from a target project's existing source tree.
 *
 * Used as a fallback when a skill's caller omits {@code basePackage}: instead of silently
 * defaulting to a hardcoded literal unrelated to the target project, we look for the first
 * {@code .java} file already present under the project's likely source roots and read its
 * {@code package x.y.z;} declaration.
 */
public final class PackageInference {

  private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

  private PackageInference() {}

  /**
   * Returns the package declared by the first {@code .java} file found under {@code projectRoot},
   * or {@link Optional#empty()} if the project has no Java source yet (e.g. a fresh scaffold).
   */
  public static Optional<String> inferBasePackage(Path projectRoot) {
    if (projectRoot == null || !Files.isDirectory(projectRoot)) return Optional.empty();
    for (Path sourceRoot : List.of(
        projectRoot.resolve("src/main/java"),
        projectRoot.resolve("src/test/java"),
        projectRoot)) {
      if (!Files.isDirectory(sourceRoot)) continue;
      Optional<String> found = findPackageIn(sourceRoot);
      if (found.isPresent()) return found;
    }
    return Optional.empty();
  }

  private static Optional<String> findPackageIn(Path root) {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !isExcluded(p))
          .sorted()
          .map(PackageInference::readPackage)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static boolean isExcluded(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.contains("/target/") || normalized.contains("/.git/");
  }

  private static Optional<String> readPackage(Path javaFile) {
    try {
      String content = Files.readString(javaFile, StandardCharsets.UTF_8);
      Matcher m = PACKAGE_DECLARATION.matcher(content);
      return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
