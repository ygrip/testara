package io.github.ygrip.testara.agent.index;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for testara-all.4: collectJavaSourceRoots added the project root AND every
 * module directory to the roots list, but scanJavaFiles(root) already recurses into modules -
 * every step definition and driver was indexed twice.
 */
class ProjectIndexerTest {

  @Test
  void doesNotDoubleScanStepDefinitionsAndDriversAcrossModuleRoots(@TempDir Path projectRoot) throws IOException {
    Files.writeString(projectRoot.resolve("pom.xml"), """
        <project>
          <modules>
            <module>my-module</module>
          </modules>
        </project>
        """);

    Path srcMain = projectRoot.resolve("my-module/src/main/java/com/example");
    Files.createDirectories(srcMain);

    Files.writeString(srcMain.resolve("MySteps.java"), """
        package com.example;
        import io.cucumber.java.en.Given;
        public class MySteps {
          @Given("a precondition")
          public void aPrecondition() { }
        }
        """);

    Files.writeString(srcMain.resolve("MyDriver.java"), """
        package com.example;
        @DriverMetadata(name = "chrome", engine = SomeEngine.class, platforms = {DESKTOP}, browserName = "chrome")
        public class MyDriver { }
        """);

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    assertEquals(1, profile.stepDefinitions().size(),
        "step definition must be indexed once, not once per overlapping source root");
    assertEquals(1, profile.drivers().size(),
        "driver must be indexed once, not once per overlapping source root");
  }
}
