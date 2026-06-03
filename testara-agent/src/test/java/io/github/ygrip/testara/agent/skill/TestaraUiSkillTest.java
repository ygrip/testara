package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraUiSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void pageTemplateUsesCurrentSeleniumPackagesAndLocatorFields() {
    String output = new TestaraUiSkill().execute(
        new TestaraUiSkill.Input("page", "login", null, "selenium", "io.github.ygrip.automation"),
        context());

    assertTrue(output.contains("src/main/java/io/github/ygrip/automation/page/LoginPage.java"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.model.Page;"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.model.DeviceType;"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.model.Locator;"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.selenium.page.SeleniumPage;"));
    assertTrue(output.contains("private static final Locator USERNAME_FIELD"));
    assertTrue(output.contains("private static final Locator ERROR_MESSAGE"));
    assertFalse(output.contains("PageElement"));
    assertFalse(output.contains("io.github.ygrip.testara.ui.page.Page;"));
    assertFalse(output.contains("io.github.ygrip.testara.ui.selenium.SeleniumPage"));
  }

  @Test
  void actionTemplateUsesCurrentUserActionPackagesAndHorizontalDataTable() {
    String output = new TestaraUiSkill().execute(
        new TestaraUiSkill.Input("action", "login", "login with credentials", "selenium",
            "io.github.ygrip.automation"),
        context());

    assertTrue(output.contains("src/main/java/io/github/ygrip/automation/action/LoginActions.java"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.executor.UserAction;"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.model.OnPage;"));
    assertTrue(output.contains("import io.github.ygrip.testara.ui.model.Action;"));
    assertTrue(output.contains("@OnPage(value = {LoginPage.class})"));
    assertTrue(output.contains("public class LoginActions extends UserAction"));
    assertFalse(output.contains("public static class"));
    assertTrue(output.contains("| username"));
    assertTrue(output.contains("| password"));
    assertTrue(output.contains("properties(test.user.username)"));
    assertTrue(output.contains("properties(test.user.password)"));
    assertFalse(output.contains("io.github.ygrip.testara.ui.actor"));
    assertFalse(output.contains("type("));
    assertFalse(output.contains("click("));
  }

  @Test
  void actionTemplateInfersLoginPageAndReusableActionName() {
    String output = new TestaraUiSkill().execute(
        new TestaraUiSkill.Input("action", null, "login with valid credentials", "selenium",
            "io.github.ygrip.automation"),
        context());

    assertTrue(output.contains("src/main/java/io/github/ygrip/automation/action/LoginActions.java"));
    assertTrue(output.contains("import io.github.ygrip.automation.page.LoginPage;"));
    assertTrue(output.contains("@OnPage(value = {LoginPage.class})"));
    assertTrue(output.contains("@Action(\"login with valid credentials\")"));
    assertTrue(output.contains("public class LoginActions extends UserAction"));
    assertFalse(output.contains("public static class"));
    assertTrue(output.contains("When user do \"login with valid credentials\" in \"login\" page with parameter"));
    assertTrue(output.contains("|key|value|"));
    assertTrue(output.contains("| username"));
    assertTrue(output.contains("properties(test.user.username)"));
    assertTrue(output.contains("| password"));
    assertTrue(output.contains("properties(test.user.password)"));
    assertFalse(output.contains("| username                       | password"));
    assertFalse(output.contains("SampleActions"));
    assertFalse(output.contains("SamplePage"));
  }

  @Test
  void configTemplateIncludesRequiredScanLocations() {
    String output = new TestaraUiSkill().execute(
        new TestaraUiSkill.Input("config", null, null, "selenium", "io.github.ygrip.automation"),
        context());

    assertTrue(output.contains("class.loader.default-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("selenium.driver.page-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("selenium.driver.action-scan-locations=io.github.ygrip.testara,io.github.ygrip.automation"));
    assertTrue(output.contains("selenium.driver.headless=false"));
    assertFalse(output.contains("selenium.driver.headless=true"));
  }

  @Test
  void pagePropertyKeysUseKebabCaseAndEnvPlaceholders() {
    String output = new TestaraUiSkill().execute(
        new TestaraUiSkill.Input("page", "cart page", null, "selenium", "io.github.ygrip.automation"),
        context());

    assertTrue(output.contains("@Page(name = \"cart-page\""));
    // Both device types get an entry in application.properties
    assertTrue(output.contains("web.page.default.cart-page.url=http://localhost:3000/cart-page"));
    assertTrue(output.contains("web.page.desktop.cart-page.url=http://localhost:3000/cart-page"));
    assertFalse(output.contains("cart page.url"));
    assertTrue(output.contains("application.properties"));
  }

  @Test
  void writePageCreatesJavaFileAndApplicationPropertyIdempotently() throws IOException {
    TestaraUiSkill skill = new TestaraUiSkill();
    String output = skill.execute(
        new TestaraUiSkill.Input("page", "login", null, "selenium", "io.github.ygrip.automation"),
        writeContext());
    String secondOutput = skill.execute(
        new TestaraUiSkill.Input("page", "login", null, "selenium", "io.github.ygrip.automation"),
        writeContext());

    Path pageFile = projectRoot.resolve("src/main/java/io/github/ygrip/automation/page/LoginPage.java");
    Path propertiesFile = projectRoot.resolve("src/test/resources/application.properties");
    String properties = Files.readString(propertiesFile);

    assertTrue(output.contains("written: src/main/java/io/github/ygrip/automation/page/LoginPage.java"));
    assertTrue(output.contains("updated application.properties"));
    assertTrue(secondOutput.contains("application.properties unchanged"));
    assertTrue(Files.exists(pageFile));
    assertTrue(Files.readString(pageFile).contains("public class LoginPage extends SeleniumPage"));
    // Both device-type entries written exactly once
    assertEquals(1, properties.lines().filter(l -> l.equals("web.page.default.login.url=http://localhost:3000/login")).count());
    assertEquals(1, properties.lines().filter(l -> l.equals("web.page.desktop.login.url=http://localhost:3000/login")).count());
  }

  @Test
  void writeActionCreatesTopLevelUserActionFile() throws IOException {
    String output = new TestaraUiSkill().execute(
        new TestaraUiSkill.Input("action", "login", "login with credentials", "selenium",
            "io.github.ygrip.automation"),
        writeContext());

    Path actionFile = projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java");
    String source = Files.readString(actionFile);

    assertTrue(output.contains("written: src/main/java/io/github/ygrip/automation/action/LoginActions.java"));
    assertTrue(source.contains("@OnPage(value = {LoginPage.class})"));
    assertTrue(source.contains("public class LoginActions extends UserAction"));
    assertFalse(source.contains("public static class"));
  }

  @Test
  void validatePageReportsSelectorStatusFromHtmlSnapshot() {
    TestaraUiSkill skill = new TestaraUiSkill();
    skill.execute(new TestaraUiSkill.Input("page", "login", null, "selenium", "io.github.ygrip.automation"),
        writeContext());

    String output = skill.execute(new TestaraUiSkill.Input("validate-page", "login", null, "selenium",
            "io.github.ygrip.automation", """
                <input id="user-name">
                <input id="password">
                <button id="login-button"></button>
                """),
        context());

    assertTrue(output.contains("selector-validation:"));
    assertTrue(output.contains("found USERNAME_FIELD id=user-name"));
    assertTrue(output.contains("found PASSWORD_FIELD id=password"));
    assertTrue(output.contains("found BUTTON_LOGIN id=login-button"));
    assertTrue(output.contains("missing ERROR_MESSAGE css=[data-test='error']"));
    assertTrue(output.contains("no data-test=\"error\" found"));
  }

  @Test
  void validatePageCanListSelectorsWhenHtmlSnapshotIsMissing() {
    TestaraUiSkill skill = new TestaraUiSkill();
    skill.execute(new TestaraUiSkill.Input("page", "login", null, "selenium", "io.github.ygrip.automation"),
        writeContext());

    String output = skill.execute(new TestaraUiSkill.Input("validate-page", "login", null, "selenium",
            "io.github.ygrip.automation"),
        context());

    assertTrue(output.contains("status: needs_html_snapshot"));
    assertTrue(output.contains("unchecked USERNAME_FIELD id=user-name"));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }

  private AgentContext writeContext() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null,
        Map.of("format", "concise", "write", "true"));
  }
}
