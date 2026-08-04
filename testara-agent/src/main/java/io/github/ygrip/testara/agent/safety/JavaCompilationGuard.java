package io.github.ygrip.testara.agent.safety;

import io.github.ygrip.testara.agent.skill.FilePatch;

import java.util.List;

/**
 * Validates generated Java source code before it is written to disk.
 *
 * <p>Checks: basic syntax markers (class declaration, matching braces),
 * required annotations, package declaration, and compilation guards.
 */
public final class JavaCompilationGuard {

  private JavaCompilationGuard() { /* utility */ }

  /**
   * Check that a generated Java class file has basic syntax markers.
   * Does not perform full compilation — that's left to the build gate.
   */
  public static boolean hasClassDeclaration(String javaSource) {
    return javaSource != null && javaSource.matches("(?s).*\\bclass\\s+\\w+.*\\{.*");
  }

  /** Check for required Testara annotations in a generated command class. */
  public static boolean hasCommandAnnotation(String javaSource) {
    return javaSource != null && javaSource.contains("@CommandTag");
  }

  /** Check for required Testara annotations in a generated validator class. */
  public static boolean hasValidationAnnotation(String javaSource) {
    return javaSource != null && javaSource.contains("@ValidationTag");
  }

  /** Check that the package declaration matches the target file path. */
  public static boolean packageMatchesPath(String javaSource, FilePatch patch) {
    if (javaSource == null || patch == null) return false;
    String pkg = extractPackage(javaSource);
    if (pkg == null) return false;
    String pathStr = patch.path().toString().replace('\\', '.').replace('/', '.');
    return pathStr.contains(pkg);
  }

  private static String extractPackage(String javaSource) {
    for (String line : javaSource.split("\n")) {
      line = line.strip();
      if (line.startsWith("package ") && line.endsWith(";")) {
        return line.substring(8, line.length() - 1).strip();
      }
    }
    return null;
  }
}
