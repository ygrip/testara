package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraBootstrapSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void writesUiBundleWithPageActionAndApplicationProperty() throws IOException {
    String output = new TestaraBootstrapSkill().execute(
        new TestaraBootstrapSkill.Input("ui-bundle", "login with credentials", "login",
            "login with credentials", null, null, null, null, "io.github.ygrip.automation", "selenium"),
        writeContext());

    Path page = projectRoot.resolve("src/main/java/io/github/ygrip/automation/page/LoginPage.java");
    Path action = projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java");
    Path properties = projectRoot.resolve("src/test/resources/application.properties");

    assertTrue(output.contains("artifact: ui-bundle"));
    assertTrue(Files.readString(page).contains("public class LoginPage extends SeleniumPage"));
    assertTrue(Files.readString(action).contains("@OnPage(value = {LoginPage.class})"));
    assertTrue(Files.readString(action).contains("public class LoginActions extends UserAction"));
    assertTrue(Files.readString(properties).contains("web.page.default.login.url=http://localhost:3000/login"));
    assertTrue(Files.readString(properties).contains("web.page.desktop.login.url=http://localhost:3000/login"));
  }

  @Test
  void writesBatchUiPagesAndActionsWithCatalogSummary() throws IOException {
    String pages = """
        [
          {"name":"login","actions":["login with valid credentials","show login error"]},
          {"name":"inventory","actions":["add product to cart"]},
          {"name":"cart","actions":["open cart page"]}
        ]
        """;

    String output = new TestaraBootstrapSkill().execute(
        new TestaraBootstrapSkill.Input("batch", null, null, null,
            null, null, null, null, "io.github.ygrip.automation", "selenium",
            "batch", pages, null, "https://www.saucedemo.com"),
        writeContext());

    assertTrue(output.contains("artifact: ui-batch"));
    assertTrue(output.contains("pages: 3"));
    assertTrue(output.contains("actions: 4"));
    assertTrue(output.contains("login with valid credentials -> loginWithValidCredentials"));
    assertTrue(output.contains("show login error -> showLoginError"));
    assertTrue(output.contains("nextRecommendedCommand: testara_run --tags @regression"));
    assertTrue(Files.exists(projectRoot.resolve("src/main/java/io/github/ygrip/automation/page/LoginPage.java")));
    assertTrue(Files.exists(projectRoot.resolve("src/main/java/io/github/ygrip/automation/page/InventoryPage.java")));
    assertTrue(Files.exists(projectRoot.resolve("src/main/java/io/github/ygrip/automation/page/CartPage.java")));
    String loginActions = Files.readString(projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java"));
    assertTrue(loginActions.contains("@Action(\"login with valid credentials\")"));
    assertTrue(loginActions.contains("@Action(\"show login error\")"));
  }

  @Test
  void writesCommandSkeletonUnderMainJavaWithScanHint() throws IOException {
    String output = new TestaraBootstrapSkill().execute(
        new TestaraBootstrapSkill.Input("command", "customer code", null,
            null, null, null, null, null, "com.acme.tests", null),
        writeContext());

    Path command = projectRoot.resolve("src/main/java/com/acme/tests/command/CustomerCodeCommand.java");
    String source = Files.readString(command);

    assertTrue(output.contains("written: src/main/java/com/acme/tests/command/CustomerCodeCommand.java"));
    assertTrue(output.contains("command.executor.scan-locations=io.github.ygrip.testara,com.acme.tests.command"));
    assertTrue(source.contains("@CommandTag(command = \"customer-code\")"));
    assertTrue(source.contains("implements CommandLogic<String>"));
    assertFalse(output.contains("src/test/java"));
  }

  @Test
  void previewsValidationSkeletonWithMainJavaPlacement() {
    String output = new TestaraBootstrapSkill().execute(
        new TestaraBootstrapSkill.Input("validation", "valid order status", null,
            null, null, null, null, null, "com.acme.tests", null),
        context());

    assertTrue(output.contains("file_path: src/main/java/com/acme/tests/validation/ValidOrderStatusValidator.java"));
    assertTrue(output.contains("@ValidationTag(command = \"valid-order-status\")"));
    assertTrue(output.contains("extends ValidatorLogic<Object, Object>"));
    assertTrue(output.contains("validator.helper.scan-locations=io.github.ygrip.testara,com.acme.tests.validation"));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, profile(), AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }

  private AgentContext writeContext() {
    return new AgentContext(projectRoot, profile(), AgentMode.READ_ONLY, null,
        Map.of("format", "concise", "write", "true"));
  }

  private TestaraProjectProfile profile() {
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
  }
}
