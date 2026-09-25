package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Infers a Java base package from a target project's existing source tree.
 *
 * Used as a fallback when a skill's caller omits {@code basePackage}: instead of silently
 * defaulting to a hardcoded literal unrelated to the target project, we read the
 * {@code package x.y.z;} declarations already present under the project's likely source roots.
 */
public final class PackageInference {

  private static final Logger LOG = Logger.getLogger(PackageInference.class.getName());
  private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
  private static final Set<String> ARTIFACT_SEGMENTS =
      Set.of("page", "pages", "action", "actions", "command", "commands", "validation", "validations",
          "data", "steps");

  private PackageInference() {}

  /**
   * Returns the project's base package, or {@link Optional#empty()} if the project has no Java source
   * yet (e.g. a fresh scaffold). Artifact sub-packages ({@code .page}, {@code .action},
   * {@code .command}, {@code .validation}, {@code .data}, {@code .steps}) are stripped and the common
   * prefix of the remaining packages is used, so callers appending {@code .page} never produce
   * {@code base.page.page}.
   */
  public static Optional<String> inferBasePackage(Path projectRoot) {
    if (projectRoot == null || !Files.isDirectory(projectRoot)) return Optional.empty();
    for (Path sourceRoot : List.of(
        projectRoot.resolve("src/main/java"),
        projectRoot.resolve("src/test/java"),
        projectRoot)) {
      if (!Files.isDirectory(sourceRoot)) continue;
      List<String> packages = packagesIn(sourceRoot);
      if (!packages.isEmpty()) return Optional.of(commonBase(packages));
    }
    return Optional.empty();
  }

  private static String commonBase(List<String> packages) {
    List<String[]> bases = packages.stream().map(PackageInference::stripArtifactSuffix).toList();
    String[] first = bases.get(0);
    int common = first.length;
    for (String[] base : bases) {
      int i = 0;
      while (i < common && i < base.length && first[i].equals(base[i])) i++;
      common = i;
    }
    if (common == 0) return String.join(".", first);
    return String.join(".", Arrays.copyOf(first, common));
  }

  /** Cuts a package at its first artifact segment (keeping at least one segment). */
  private static String[] stripArtifactSuffix(String pkg) {
    String[] segments = pkg.split("\\.");
    for (int i = 1; i < segments.length; i++) {
      if (ARTIFACT_SEGMENTS.contains(segments[i])) return Arrays.copyOf(segments, i);
    }
    return segments;
  }

  private static List<String> packagesIn(Path root) {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !isExcluded(p))
          .sorted()
          .map(PackageInference::readPackage)
          .flatMap(Optional::stream)
          .distinct()
          .toList();
    } catch (IOException e) {
      LOG.warning("Cannot scan " + root + " for package declarations: " + e.getMessage());
      return List.of();
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
      LOG.warning("Cannot read " + javaFile + ": " + e.getMessage());
      return Optional.empty();
    }
  }
}
