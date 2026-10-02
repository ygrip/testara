package io.github.ygrip.testara.agent.skill.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MavenCommandBuilderTest {

  private final MavenCommandBuilder builder = new MavenCommandBuilder();

  @Test
  void buildsDefaultVerifyCommand() {
    String cmd = builder.build("@smoke and @api");
    assertEquals("mvn verify -Dcucumber.filter.tags=\"@smoke and @api\"", cmd);
  }

  @Test
  void buildsCommandWithModule() {
    String cmd = builder.build("@payment and @smoke", "automation-tests", false);
    assertEquals("mvn -pl automation-tests test -Dcucumber.filter.tags=\"@payment and @smoke\"", cmd);
  }

  @Test
  void buildsVerifyCommand() {
    String cmd = builder.build("@smoke", null, true);
    assertEquals("mvn verify -Dcucumber.filter.tags=\"@smoke\"", cmd);
  }

  @Test
  void buildsModuleVerifyCommand() {
    String cmd = builder.build("@api", "api-tests", true);
    assertEquals("mvn -pl api-tests verify -Dcucumber.filter.tags=\"@api\"", cmd);
  }

  @Test
  void rejectsBlankTagExpression() {
    assertThrows(IllegalArgumentException.class, () -> builder.build(""));
    assertThrows(IllegalArgumentException.class, () -> builder.build(null));
  }

  @Test
  void rejectsUnsafeCharactersInTagExpression() {
    assertThrows(IllegalArgumentException.class, () -> builder.build("@smoke; rm -rf /"));
    assertThrows(IllegalArgumentException.class, () -> builder.build("@smoke && echo hacked"));
  }

  @Test
  void rejectsUnsafeModuleName() {
    assertThrows(IllegalArgumentException.class,
        () -> builder.build("@smoke", "module; rm -rf /", false));
  }

  @Test
  void acceptsComplexTagExpression() {
    String cmd = builder.build("(@api or @ui) and @smoke and not @slow");
    assertTrue(cmd.contains("@api or @ui"), "Should accept complex tag expression");
  }

  @Test
  void acceptsParenthesizedExpression() {
    String cmd = builder.build("@checkout and (@P0 or @critical)");
    assertTrue(cmd.contains("@checkout"), "Should accept parenthesized expression");
  }

  @Test
  void buildsArgvAsDiscreteTokensNoShellQuoting() {
    List<String> argv = builder.buildArgv("@smoke and @api", null, true);
    assertEquals(List.of("verify", "-Dcucumber.filter.tags=@smoke and @api"), argv);
  }

  @Test
  void buildsArgvWithModule() {
    List<String> argv = builder.buildArgv("@payment", "automation-tests", false);
    assertEquals(List.of("test", "-pl", "automation-tests", "-Dcucumber.filter.tags=@payment"), argv);
  }

  @Test
  void buildArgvRejectsUnsafeTagExpression() {
    assertThrows(IllegalArgumentException.class, () -> builder.buildArgv("@smoke; rm -rf /", null, true));
  }

  @Test
  void buildsRerunArgvUsingNativeFeaturePathSyntax() {
    Path rerunFile = Path.of("target/rerun/rerun.txt");
    List<String> argv = builder.buildRerunArgv(rerunFile, null, true);
    assertEquals(List.of("verify", "-Dcucumber.features=@target/rerun/rerun.txt"), argv);
  }

  @Test
  void buildsRerunArgvWithModule() {
    Path rerunFile = Path.of("target/rerun/rerun.txt");
    List<String> argv = builder.buildRerunArgv(rerunFile, "api-tests", false);
    assertEquals(List.of("test", "-pl", "api-tests", "-Dcucumber.features=@target/rerun/rerun.txt"), argv);
  }

  @Test
  void acceptsNestedModulePathAndArtifactIdSelector() {
    assertEquals(List.of("verify", "-pl", "modules/api", "-Dcucumber.filter.tags=@api"),
        builder.buildArgv("@api", "modules/api", true));
    assertEquals(List.of("verify", "-pl", ":api-tests", "-Dcucumber.filter.tags=@api"),
        builder.buildArgv("@api", ":api-tests", true));
  }

  @Test
  void rejectsTraversalAndAbsoluteModulePaths() {
    for (String module : List.of("../other", "modules/../../x", "/etc", "modules//api", "C:\\\\x", ":", "a b")) {
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
          () -> builder.buildArgv("@api", module, true), module);
      assertTrue(error.getMessage().contains(module));
    }
  }

  @Test
  void commandPrependsProjectWrapperAndKeepsDisplayForm(@TempDir Path projectRoot) throws IOException {
    Files.writeString(projectRoot.resolve("mvnw"), "#!/bin/sh\n");

    BuildCommand command = builder.command(projectRoot, "@smoke", null);

    assertEquals(List.of(projectRoot.resolve("mvnw").toAbsolutePath().toString(), "verify",
        "-Dcucumber.filter.tags=@smoke"), command.argv());
    assertEquals("mvn verify -Dcucumber.filter.tags=\"@smoke\"", command.display());
  }

  @Test
  void buildRerunArgvRejectsUnsafeModuleName() {
    Path rerunFile = Path.of("target/rerun/rerun.txt");
    assertThrows(IllegalArgumentException.class,
        () -> builder.buildRerunArgv(rerunFile, "module; rm -rf /", true));
  }
}
