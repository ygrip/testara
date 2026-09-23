package io.github.ygrip.testara.agent.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectPathGuardTest {

  @Test
  void resolvesNormalRelativePath(@TempDir Path root) {
    Path path = ProjectPathGuard.resolveInside(root, "src/test/resources/example.json");
    assertEquals(root.resolve("src/test/resources/example.json").toAbsolutePath().normalize(), path);
  }

  @Test
  void rejectsTraversalOutsideProject(@TempDir Path root) {
    assertThrows(IllegalArgumentException.class,
        () -> ProjectPathGuard.resolveInside(root, "../outside.txt"));
  }

  @Test
  void rejectsAbsolutePath(@TempDir Path root) {
    assertThrows(IllegalArgumentException.class,
        () -> ProjectPathGuard.resolveInside(root, root.resolve("outside.txt").toAbsolutePath().toString()));
  }
}
