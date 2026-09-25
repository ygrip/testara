package io.github.ygrip.testara.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactFilesTest {

  @TempDir
  Path root;

  @Test
  void keepsExistingFilesUnlessOverwriteIsRequested() throws IOException {
    ArtifactFiles.Written first = ArtifactFiles.write(root, "a/b.txt", "one", false);
    ArtifactFiles.Written second = ArtifactFiles.write(root, "a/b.txt", "two", false);

    assertEquals(ArtifactFiles.Status.CREATED, first.status());
    assertEquals(ArtifactFiles.Status.EXISTS, second.status());
    assertEquals("one", Files.readString(root.resolve("a/b.txt")));
    assertEquals(ArtifactFiles.Status.OVERWRITTEN, ArtifactFiles.write(root, "a/b.txt", "two", true).status());
    assertEquals("two", Files.readString(root.resolve("a/b.txt")));
  }

  @Test
  void pathEscapeIsReportedAsIoFailure() {
    assertThrows(IOException.class, () -> ArtifactFiles.write(root, "../outside.txt", "x", false));
  }

  @Test
  void mergesOnlyMissingPropertyKeys() throws IOException {
    Path file = root.resolve("src/test/resources/application.properties");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "existing.key=keep\n");
    List<String> candidates = List.of("src/test/resources/application.properties");

    ArtifactFiles.PropertyMerge merge = ArtifactFiles.mergeProperties(root, candidates,
        "# block\nexisting.key=replace\nnew.key=value\n");
    ArtifactFiles.PropertyMerge again = ArtifactFiles.mergeProperties(root, candidates,
        "# block\nexisting.key=replace\nnew.key=value\n");

    assertEquals(List.of("new.key"), merge.addedKeys());
    assertTrue(again.addedKeys().isEmpty());
    assertEquals("existing.key=keep\n\n# block\nnew.key=value\n", Files.readString(file));
  }

  @Test
  void rejectsJavaWhosePackageDoesNotMatchItsPath() {
    String source = "package com.acme.page;\n\npublic class LoginPage {\n}\n";

    assertThrows(IOException.class,
        () -> ArtifactFiles.writeJava(root, "src/main/java/com/other/page/LoginPage.java", source, false, false, false));
  }
}
