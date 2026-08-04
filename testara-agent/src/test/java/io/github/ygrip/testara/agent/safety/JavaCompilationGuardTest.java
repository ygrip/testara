package io.github.ygrip.testara.agent.safety;

import java.nio.file.Path;

import io.github.ygrip.testara.agent.skill.FilePatch;
import io.github.ygrip.testara.agent.skill.FilePatchOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-all.7: packageMatchesPath only replaced forward slashes before
 * comparing against the dotted package name, so a Windows-style path (backslash separators) never
 * matched even when the package genuinely corresponded to the file's directory.
 */
class JavaCompilationGuardTest {

  private static final String JAVA_SOURCE = """
      package com.example.command;

      public class FooCommand {
      }
      """;

  @Test
  void matchesUnixStylePath() {
    FilePatch patch = new FilePatch(Path.of("src/main/java/com/example/command/FooCommand.java"),
        FilePatchOperation.CREATE, JAVA_SOURCE, "test");

    assertTrue(JavaCompilationGuard.packageMatchesPath(JAVA_SOURCE, patch));
  }

  @Test
  void matchesWindowsStylePath() {
    FilePatch patch = new FilePatch(Path.of("src\\main\\java\\com\\example\\command\\FooCommand.java"),
        FilePatchOperation.CREATE, JAVA_SOURCE, "test");

    assertTrue(JavaCompilationGuard.packageMatchesPath(JAVA_SOURCE, patch),
        "a backslash-separated path must still match the dotted package name");
  }

  @Test
  void rejectsMismatchedPackage() {
    FilePatch patch = new FilePatch(Path.of("src/main/java/com/other/command/FooCommand.java"),
        FilePatchOperation.CREATE, JAVA_SOURCE, "test");

    assertFalse(JavaCompilationGuard.packageMatchesPath(JAVA_SOURCE, patch));
  }
}
