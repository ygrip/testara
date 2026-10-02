package io.github.ygrip.testara.agent.safety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves user-derived artifact paths without allowing workspace escape. */
public final class ProjectPathGuard {

  private ProjectPathGuard() {}

  public static Path resolveInside(Path projectRoot, String relativePath) {
    if (projectRoot == null) throw new IllegalArgumentException("Project root is required");
    if (relativePath == null || relativePath.isBlank()) {
      throw new IllegalArgumentException("Artifact path must not be blank");
    }

    Path root = projectRoot.toAbsolutePath().normalize();
    Path relative = Path.of(relativePath);
    if (relative.isAbsolute()) {
      throw new IllegalArgumentException("Absolute artifact paths are not allowed: " + relativePath);
    }

    Path resolved = root.resolve(relative).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Artifact path escapes project root: " + relativePath);
    }

    verifyExistingAncestor(root, resolved);
    return resolved;
  }

  private static void verifyExistingAncestor(Path root, Path resolved) {
    if (!Files.exists(root)) return;
    try {
      Path realRoot = root.toRealPath();
      if (Files.isSymbolicLink(resolved) && !Files.exists(resolved)) {
        // Writing through a dangling link would create its (unchecked) target
        throw new IllegalArgumentException("Artifact path is a dangling symbolic link: " + resolved);
      }
      if (Files.exists(resolved) && !resolved.toRealPath().startsWith(realRoot)) {
        throw new IllegalArgumentException("Artifact path escapes project root through a symbolic link");
      }
      Path ancestor = Files.isDirectory(resolved) ? resolved : resolved.getParent();
      while (ancestor != null && ancestor.startsWith(root) && !Files.exists(ancestor)) {
        ancestor = ancestor.getParent();
      }
      if (ancestor != null && ancestor.startsWith(root)
          && !ancestor.toRealPath().startsWith(realRoot)) {
        throw new IllegalArgumentException("Artifact path escapes project root through a symbolic link");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot validate artifact path: " + resolved, e);
    }
  }
}
