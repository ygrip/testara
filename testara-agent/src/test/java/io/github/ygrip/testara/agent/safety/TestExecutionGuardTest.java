package io.github.ygrip.testara.agent.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestExecutionGuardTest {

  @Test
  void acceptsSafeMavenCommand() {
    assertNull(TestExecutionGuard.validateCommand("mvn test -Dcucumber.filter.tags=\"@smoke\""));
    assertNull(TestExecutionGuard.validateCommand("./mvnw test -Dcucumber.filter.tags=\"@api and @smoke\""));
    assertNull(TestExecutionGuard.validateCommand("mvnw verify"));
  }

  @Test
  void rejectsNonMavenCommand() {
    String error = TestExecutionGuard.validateCommand("echo hello");
    assertNotNull(error);
    assertTrue(error.contains("must start with 'mvn'"));
  }

  @Test
  void rejectsChainedCommands() {
    String error = TestExecutionGuard.validateCommand("mvn test && rm -rf /");
    assertNotNull(error);
    assertTrue(error.contains("blocked shell pattern"));
  }

  @Test
  void rejectsSemicolons() {
    String error = TestExecutionGuard.validateCommand("mvn test; echo hacked");
    assertNotNull(error);
    assertTrue(error.contains("blocked shell pattern"));
  }

  @Test
  void rejectsPipes() {
    String error = TestExecutionGuard.validateCommand("mvn test | grep FAIL");
    assertNotNull(error);
  }

  @Test
  void rejectsBackticks() {
    String error = TestExecutionGuard.validateCommand("mvn test `whoami`");
    assertNotNull(error);
  }

  @Test
  void rejectsCommandSubstitution() {
    String error = TestExecutionGuard.validateCommand("mvn test $(whoami)");
    assertNotNull(error);
    assertTrue(error.contains("blocked shell pattern"));
  }

  @Test
  void rejectsBlankCommand() {
    assertNotNull(TestExecutionGuard.validateCommand(""));
    assertNotNull(TestExecutionGuard.validateCommand(null));
  }

  @Test
  void validatesTagExpression() {
    assertTrue(TestExecutionGuard.isValidTagExpression("@smoke"));
    assertTrue(TestExecutionGuard.isValidTagExpression("@api and @smoke"));
    assertTrue(TestExecutionGuard.isValidTagExpression("(@api or @ui) and not @slow"));
  }

  @Test
  void rejectsUnsafeTagExpressionCharacters() {
    assertFalse(TestExecutionGuard.isValidTagExpression("@smoke; rm -rf"));
    assertFalse(TestExecutionGuard.isValidTagExpression("@smoke && echo"));
    assertFalse(TestExecutionGuard.isValidTagExpression(""));
    assertFalse(TestExecutionGuard.isValidTagExpression(null));
  }
}
