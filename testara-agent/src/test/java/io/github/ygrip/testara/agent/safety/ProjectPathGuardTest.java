package io.github.ygrip.testara.agent.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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

  @Test
  void rejectsExistingFileSymlinkPointingOutsideProject(@TempDir Path root, @TempDir Path outside) throws IOException {
    Path secret = Files.writeString(outside.resolve("secret.properties"), "x=1");
    Files.createDirectories(root.resolve("src/test/resources"));
    Files.createSymbolicLink(root.resolve("src/test/resources/application.properties"), secret);

    assertThrows(IllegalArgumentException.class,
        () -> ProjectPathGuard.resolveInside(root, "src/test/resources/application.properties"));
  }

  @Test
  void rejectsDanglingSymlink(@TempDir Path root, @TempDir Path outside) throws IOException {
    Files.createSymbolicLink(root.resolve("generated.feature"), outside.resolve("missing.feature"));

    assertThrows(IllegalArgumentException.class, () -> ProjectPathGuard.resolveInside(root, "generated.feature"));
  }
}
