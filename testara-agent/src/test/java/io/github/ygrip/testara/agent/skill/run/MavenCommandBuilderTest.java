package io.github.ygrip.testara.agent.skill.run;

import org.junit.jupiter.api.Test;
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
}
