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
    String basePkg = input.basePackage() != null ? input.basePackage() : "io.github.ygrip.testara";
    boolean concise = "concise".equals(context.options().get("format"));
    boolean write = "true".equals(context.options().get("write"));

    return switch (mode) {
      case "page"    -> generatePage(input.pageName(), basePkg, context.projectRoot(), write, concise);
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
          - UserAction: 3+ operations on same page → @OnPage + @Action methods → user do "action" in "page" page
          - Page: @Page(name="login", url="") + web.page.desktop.login.url=properties(app.web.login-url)
          - PageFinder: resolves elements by name from @Page class (prefer named elements over raw selectors)
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
        }
        ```
        URL in properties:
        ```properties
        web.page.desktop.login.url=properties(app.web.login-url)
        app.web.login-url=http://localhost:3000/login
        ```

        ## UserAction class
        ```java
        @OnPage(LoginPage.class)
        public class LoginActions extends UserAction {
          @Action("login with credential")
          public void loginWithCredential(Map<String, Object> params) {
            attemptsTo(
                Enter.text(String.valueOf(params.get("username"))).into("emailInput"),
                Enter.text(String.valueOf(params.get("password"))).into("passwordInput"),
                Click.on("loginButton")
            );
          }
        }
        ```
        Feature usage:
        ```gherkin
        When user do "login with credential" in "login" page with parameter
          | username | properties(test.user.email)    |
          | password | properties(test.user.password) |
        ```
        """;
  }

  private String generatePage(String pageName, String basePkg, Path root, boolean write, boolean concise) {
    if (pageName == null) pageName = "sample";
    String pName = pageName.toLowerCase(Locale.ROOT);
    String pClass = toClassName(pageName) + "Page";
    String pkgPath = basePkg.replace('.', '/');

    String source = """
        package %s.pages;

        import io.github.ygrip.testara.ui.page.DeviceType;
        import io.github.ygrip.testara.ui.page.Page;
        import io.github.ygrip.testara.ui.selenium.SeleniumPage;

        /**
         * Page: %s
         * URL configured via: web.page.desktop.%s.url=properties(app.web.%s-url)
         */
        @Page(name = "%s", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
        public class %s extends SeleniumPage {
        }
        """.formatted(basePkg, pageName, pName, pName, pName, pClass);

    String propEntry = "web.page.desktop." + pName + ".url=properties(app.web." + pName + "-url)\napp.web." + pName + "-url=http://localhost:3000/" + pName;
    String relativePath = "src/test/java/" + pkgPath + "/pages/" + pClass + ".java";

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
        package %s.actions;

        import %s.pages.%s;
        import io.github.ygrip.testara.ui.actor.UserAction;
        import io.github.ygrip.testara.ui.actor.OnPage;
        import io.github.ygrip.testara.ui.actor.Action;
        import io.github.ygrip.testara.ui.interaction.Click;
        import io.github.ygrip.testara.ui.interaction.Enter;

        import java.util.Map;

        @OnPage(%s.class)
        public class %s extends UserAction {

          @Action("%s")
          public void %s(Map<String, Object> params) {
            // TODO: implement using Testara interactions
            // attemptsTo(
            //     Enter.text(String.valueOf(params.get("field"))).into("element"),
            //     Click.on("submitButton")
            // );
          }
        }
        """.formatted(basePkg, basePkg, pClass, pClass, aClass, actionName, methodName);

    String relativePath = "src/test/java/" + pkgPath + "/actions/" + aClass + ".java";
    String featureStep = "When user do \"" + actionName + "\" in \"" + pageName.toLowerCase(Locale.ROOT) + "\" page with parameter";

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
          """;
      case "appium" -> """
          automation.engine.default-engine=appium
          automation.engine.active-engines=appium
          appium.driver.platformName=Android
          appium.driver.deviceName=emulator-5554
          """;
      default -> """
          automation.engine.default-engine=selenium
          automation.engine.active-engines=selenium
          selenium.driver.headless=true
          selenium.driver.page-scan-locations=io.github.ygrip.testara,%s.pages
          selenium.driver.action-scan-locations=io.github.ygrip.testara,%s.actions
          """.formatted(basePkg, basePkg);
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
