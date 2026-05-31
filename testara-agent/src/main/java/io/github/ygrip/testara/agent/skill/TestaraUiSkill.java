package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Skill: generate Testara UI artifacts — Page, UserAction, engine config.
 *
 * Modes: explain | page | action | config
 * Rule: 3+ UI operations on the same page → generate UserAction, not custom steps.
 */
public class TestaraUiSkill implements AgentSkill<TestaraUiSkill.Input, String> {

  public record Input(String mode, String pageName, String actionName, String engine, String basePackage) {}

  @Override
  public String name() { return "testara-ui"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String mode = input.mode() != null ? input.mode() : "explain";
    String basePkg = input.basePackage() != null ? input.basePackage() : "io.github.ygrip.automation";
    boolean concise = "concise".equals(context.options().get("format"));
    boolean write = "true".equals(context.options().get("write"));

    return switch (mode) {
      case "page"    -> generatePage(input.pageName(), input.engine(), basePkg, context.projectRoot(), write, concise);
      case "action"  -> generateAction(input.pageName(), input.actionName(), basePkg, context.projectRoot(), write, concise);
      case "config"  -> generateUiConfig(input.engine(), basePkg, concise);
      default        -> explainUi(concise);
    };
  }

  private String explainUi(boolean concise) {
    if (concise) {
      return """
          testara-ui concepts:
          - UIBaseSteps: basic interactions (click, enter, wait, see) — use these first
          - UserAction: 3+ operations on same page -> @OnPage + @Action methods -> user do "action" in "page" page
          - Page: @Page(name="login", url="") + Locator fields + web.page.desktop.login.url=properties(app.web.login-url)
          - PageFinder: resolves Locator/By/WebElement fields by field-name aliases such as "username field"
          - DriverSessionManager: never create WebDriver directly, use Testara session abstractions
          - ActorManager: currentActor().attemptsTo(Click.on("element"), Enter.text("value").into("field"))
          """;
    }
    return """
        # Testara UI Guide

        ## Priority order
        1. **UIBaseSteps** — atomic interactions (click, enter, wait, navigate, assert)
        2. **UserAction** — reusable flows (3+ operations on same page)
        3. **Custom Interaction/Observation** — only for non-standard behavior
        4. **Custom Cucumber step** — last resort only

        ## Page class
        ```java
        @Page(name = "login", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
        public class LoginPage extends SeleniumPage {
          private static final Locator USERNAME_FIELD = Locator.id("user-name");
          private static final Locator PASSWORD_FIELD = Locator.id("password");
          private static final Locator BUTTON_LOGIN = Locator.id("login-button");
        }
        ```
        URL in properties:
        ```properties
        web.page.desktop.login.url=properties(app.web.login-url)
        app.web.login-url=http://localhost:3000/login
        ```

        ## UserAction class
        ```java
        @OnPage(value = {LoginPage.class})
        public class LoginActions extends UserAction {
          @Action("login with credential")
          public void loginWithCredential(Map<String, Object> params) {
            attemptsTo(
                Enter.text(String.valueOf(params.get("username"))).into("username field"),
                Enter.text(String.valueOf(params.get("password"))).into("password field"),
                Click.on("button login")
            );
          }
        }
        ```
        Feature usage:
        ```gherkin
        When user do "login with credential" in "login" page with parameter
          | username                    | password                       |
          | properties(test.user.email) | properties(test.user.password) |
        ```
        """;
  }

  private String generatePage(String pageName, String engine, String basePkg, Path root, boolean write,
      boolean concise) {
    if (pageName == null) pageName = "sample";
    String pName = pageName.toLowerCase(Locale.ROOT);
    String pClass = toClassName(pageName) + "Page";
    String pkgPath = basePkg.replace('.', '/');
    String pageBaseClass = "playwright".equalsIgnoreCase(engine) ? "PlaywrightPage" : "SeleniumPage";
    String pageBaseImport = "playwright".equalsIgnoreCase(engine)
        ? "io.github.ygrip.testara.ui.playwright.page.PlaywrightPage"
        : "io.github.ygrip.testara.ui.selenium.page.SeleniumPage";

    String source = """
        package %s.page;

        import io.github.ygrip.testara.ui.model.DeviceType;
        import io.github.ygrip.testara.ui.model.Locator;
        import io.github.ygrip.testara.ui.model.Page;
        import %s;

        /**
         * Page: %s
         * URL configured via: web.page.desktop.%s.url=properties(app.web.%s-url)
         * Element names are resolved from Locator field names, e.g. USERNAME_FIELD -> "username field".
         */
        @Page(name = "%s", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
        public class %s extends %s {
          private static final Locator USERNAME_FIELD = Locator.id("user-name");
          private static final Locator PASSWORD_FIELD = Locator.id("password");
          private static final Locator BUTTON_LOGIN = Locator.id("login-button");
        }
        """.formatted(basePkg, pageBaseImport, pageName, pName, pName, pName, pClass, pageBaseClass);

    String propEntry = "web.page.desktop." + pName + ".url=properties(app.web." + pName + "-url)\napp.web." + pName + "-url=http://localhost:3000/" + pName;
    String relativePath = "src/main/java/" + pkgPath + "/page/" + pClass + ".java";

    if (write) {
      try {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source, StandardCharsets.UTF_8);
        return concise ? "written: " + relativePath + "\nadd to configuration.properties:\n" + propEntry
            : "## Page Written\n\n`" + relativePath + "`\n\n```java\n" + source + "```\n\n**Add to configuration.properties:**\n```properties\n" + propEntry + "\n```\n";
      } catch (IOException e) {
        return "Error: " + e.getMessage();
      }
    }
    if (concise) return "path: " + relativePath + "\nprops: " + propEntry + "\n" + source;
    return "## Page: " + pClass + "\n\n**Path:** `" + relativePath + "`\n\n```java\n" + source + "```\n\n**Properties:**\n```properties\n" + propEntry + "\n```\n";
  }

  private String generateAction(String pageName, String actionName, String basePkg, Path root, boolean write, boolean concise) {
    if (pageName == null) pageName = "sample";
    if (actionName == null) actionName = "perform action";
    String pClass = toClassName(pageName) + "Page";
    String aClass = toClassName(pageName) + "Actions";
    String methodName = toCamelCase(actionName);
    String pkgPath = basePkg.replace('.', '/');

    String source = """
        package %s.action;

        import %s.page.%s;
        import io.github.ygrip.testara.ui.executor.UserAction;
        import io.github.ygrip.testara.ui.interaction.Click;
        import io.github.ygrip.testara.ui.interaction.Enter;
        import io.github.ygrip.testara.ui.model.Action;
        import io.github.ygrip.testara.ui.model.OnPage;

        import java.util.Map;

        @OnPage(value = {%s.class})
        public class %s extends UserAction {

          @Action("%s")
          public void %s(Map<String, Object> params) {
            attemptsTo(
                Enter.text(String.valueOf(params.get("username"))).into("username field"),
                Enter.text(String.valueOf(params.get("password"))).into("password field"),
                Click.on("button login")
            );
          }
        }
        """.formatted(basePkg, basePkg, pClass, pClass, aClass, actionName, methodName);

    String relativePath = "src/main/java/" + pkgPath + "/action/" + aClass + ".java";
    String featureStep = """
        When user do "%s" in "%s" page with parameter
          | username                    | password                       |
          | properties(test.user.email) | properties(test.user.password) |\
        """.formatted(actionName, pageName.toLowerCase(Locale.ROOT));

    if (write) {
      try {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source, StandardCharsets.UTF_8);
        return concise ? "written: " + relativePath + "\nstep: " + featureStep
            : "## Action Written\n\n`" + relativePath + "`\n\n```java\n" + source + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n";
      } catch (IOException e) {
        return "Error: " + e.getMessage();
      }
    }
    if (concise) return "path: " + relativePath + "\nstep: " + featureStep + "\n" + source;
    return "## UserAction: " + aClass + "\n\n**Path:** `" + relativePath + "`\n\n```java\n" + source + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n";
  }

  private String generateUiConfig(String engine, String basePkg, boolean concise) {
    if (engine == null) engine = "selenium";
    String block = switch (engine.toLowerCase(Locale.ROOT)) {
      case "playwright" -> """
          automation.engine.default-engine=playwright
          automation.engine.active-engines=playwright
          playwright.browser.headless=true
          playwright.browser.browserType=chromium
          class.loader.default-scan-locations=io.github.ygrip.testara,%s
          playwright.browser.page-scan-locations=io.github.ygrip.testara,%s
          playwright.browser.action-scan-locations=io.github.ygrip.testara,%s
          """.formatted(basePkg, basePkg, basePkg);
      case "appium" -> """
          automation.engine.default-engine=appium
          automation.engine.active-engines=appium
          appium.driver.platformName=Android
          appium.driver.deviceName=emulator-5554
          """;
      default -> """
          automation.engine.default-engine=selenium
          automation.engine.active-engines=selenium
          class.loader.default-scan-locations=io.github.ygrip.testara,%s
          selenium.driver.headless=true
          selenium.driver.page-scan-locations=io.github.ygrip.testara,%s
          selenium.driver.action-scan-locations=io.github.ygrip.testara,%s
          """.formatted(basePkg, basePkg, basePkg);
    };
    return concise ? block : "## UI Config — " + engine + "\n\n```properties\n" + block + "```\n";
  }

  private String toClassName(String name) {
    StringBuilder sb = new StringBuilder();
    for (String p : name.replaceAll("[^a-zA-Z0-9]+", " ").trim().split("\\s+"))
      if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
    return sb.toString();
  }

  private String toCamelCase(String name) {
    String[] parts = name.replaceAll("[^a-zA-Z0-9]+", " ").trim().split("\\s+");
    StringBuilder sb = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
    for (int i = 1; i < parts.length; i++)
      sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase(Locale.ROOT));
    return sb.toString();
  }
}
