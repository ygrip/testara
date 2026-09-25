package io.github.ygrip.testara.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageInferenceTest {

  @TempDir
  Path projectRoot;

  @Test
  void inferBasePackageReadsPackageDeclarationFromExistingSource() throws IOException {
    Path sourceFile = projectRoot.resolve("src/main/java/com/acme/checkout/CheckoutPage.java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, """
        package com.acme.checkout;

        public class CheckoutPage {
        }
        """, StandardCharsets.UTF_8);

    Optional<String> result = PackageInference.inferBasePackage(projectRoot);

    assertTrue(result.isPresent());
    assertEquals("com.acme.checkout", result.get());
  }

  @Test
  void inferBasePackageReturnsEmptyForFreshProject() {
    Optional<String> result = PackageInference.inferBasePackage(projectRoot);

    assertTrue(result.isEmpty());
  }

  @Test
  void inferBasePackageStripsArtifactSubPackages() throws IOException {
    write("src/main/java/com/acme/auto/page/LoginPage.java", "package com.acme.auto.page;\n\nclass LoginPage {}\n");
    write("src/main/java/com/acme/auto/action/LoginActions.java", "package com.acme.auto.action;\n\nclass LoginActions {}\n");

    assertEquals(Optional.of("com.acme.auto"), PackageInference.inferBasePackage(projectRoot));
  }

  private void write(String relative, String content) throws IOException {
    Path file = projectRoot.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }
}
