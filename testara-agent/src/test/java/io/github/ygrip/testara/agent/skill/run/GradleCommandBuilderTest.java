package io.github.ygrip.testara.agent.skill.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GradleCommandBuilderTest {

  @TempDir
  Path projectRoot;

  private final GradleCommandBuilder builder = new GradleCommandBuilder();
  private String initScript;

  @BeforeEach
  void kotlinDslProject() throws IOException {
    Files.writeString(projectRoot.resolve("settings.gradle.kts"), "include(\"modules:api\")\n");
    Files.writeString(projectRoot.resolve("build.gradle.kts"), """
        plugins { java }
        dependencies { testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0") }
        tasks.test { useJUnitPlatform() }
        """);
    initScript = projectRoot.toAbsolutePath().resolve(".testara-agent/gradle/testara-cucumber.init.gradle").toString();
  }

  @Test
  void buildsTagRunWithInitScriptAndProjectProperty() {
    BuildCommand command = builder.build(projectRoot, "@smoke and not @slow", null, null);

    assertEquals(List.of("gradle", "test", "--console=plain", "--init-script", initScript,
        "-Pcucumber.filter.tags=@smoke and not @slow"), command.argv());
    assertEquals("gradle test --console=plain --init-script " + initScript
        + " -Pcucumber.filter.tags=\"@smoke and not @slow\"", command.display());
  }

  @Test
  void usesWrapperAndModuleTaskPath() throws IOException {
    Files.writeString(projectRoot.resolve("gradlew"), "#!/bin/sh\n");

    BuildCommand command = builder.build(projectRoot, "@api", "modules/api", "integrationTest");

    assertEquals(projectRoot.resolve("gradlew").toAbsolutePath().toString(), command.argv().get(0));
    assertEquals(":modules:api:integrationTest", command.argv().get(1));
    assertTrue(command.display().startsWith("gradlew :modules:api:integrationTest --console=plain"));
  }

  @Test
  void acceptsGradleProjectPathModule() {
    assertEquals(":api:test", builder.build(projectRoot, "@api", ":api", "").argv().get(1));
  }

  @Test
  void buildsRerunWithNativeFeaturePath() {
    Path rerunFile = projectRoot.resolve("target/rerun/rerun.txt");

    BuildCommand command = builder.buildRerun(projectRoot, rerunFile, null, null);

    assertEquals("-Pcucumber.features=@" + rerunFile, command.argv().get(5));
    assertFalse(command.display().contains("cucumber.filter.tags"));
  }

  @Test
  void rejectsUnsafeInput() {
    assertThrows(IllegalArgumentException.class, () -> builder.build(projectRoot, "@smoke; rm -rf /", null, null));
    assertThrows(IllegalArgumentException.class, () -> builder.build(projectRoot, "@smoke", "../escape", null));
    assertThrows(IllegalArgumentException.class, () -> builder.build(projectRoot, "@smoke", null, "test; rm"));
    assertThrows(IllegalArgumentException.class, () -> builder.build(projectRoot, "@smoke", "/abs/path", null));
  }

  @Test
  void writesAgentOwnedInitScriptForwardingCucumberProperties() throws IOException {
    Path script = builder.writeInitScript(projectRoot);

    assertEquals(Path.of(initScript), script);
    String content = Files.readString(script);
    assertTrue(content.contains("gradle.startParameter.projectProperties"));
    assertTrue(content.contains("key.startsWith('cucumber.')"));
    assertTrue(content.contains("tasks.withType(Test).configureEach"));
    assertTrue(content.contains("systemProperties(cucumberProperties)"));
    assertTrue(content.contains("outputs.upToDateWhen { false }"));
    assertTrue(Files.readString(projectRoot.resolve("build.gradle.kts")).contains("useJUnitPlatform"),
        "user build script must stay untouched");
  }
}
