package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

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
    assertTrue(output.contains("@Action(\"login with credentials\")"));
    assertTrue(output.contains("public class LoginActions extends UserAction"));
    assertFalse(output.contains("public static class"));
    assertTrue(output.contains("When user do \"login with credentials\" in \"login\" page with parameter"));
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
    assertTrue(output.contains("web.page.desktop.cart-page.url=${APP_WEB_CART_PAGE_URL:http://localhost:3000/cart-page}"));
    assertFalse(output.contains("cart page.url"));
    assertFalse(output.contains("=properties("));
    assertTrue(output.contains("application.properties"));
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }
}
