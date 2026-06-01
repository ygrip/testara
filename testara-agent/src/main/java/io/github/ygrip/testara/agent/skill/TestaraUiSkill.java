package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
          - Page: @Page(name="{page}", url="") + Locator fields + web.page.desktop.{page}.url=properties(app.web.{page}-url)
          - PageFinder: resolves Locator/By/WebElement fields by field-name aliases such as "username field"
          - DriverSessionManager: never create WebDriver directly, use Testara session abstractions
          - ActorManager: currentActor().attemptsTo(Click.on("element"), Enter.text("value").into("field"))
          - Templates are intent-aware: login/search/checkout get specific locators; other pages get primary/success/error locators
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
          private static final Locator ERROR_MESSAGE = Locator.css("[data-test='error']");
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
          | properties(test.user.username) | properties(test.user.password) |
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
    String locators = pageLocators(pName);

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
        %s
        }
        """.formatted(basePkg, pageBaseImport, pageName, pName, pName, pName, pClass, pageBaseClass, locators);

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
    if (actionName == null || actionName.isBlank()) actionName = "perform action";
    String normalizedAction = normalizeActionName(actionName);
    if (pageName == null || pageName.isBlank()) pageName = inferPageName(normalizedAction);
    String pClass = toClassName(pageName) + "Page";
    String aClass = toClassName(pageName) + "Actions";
    String methodName = toCamelCase(normalizedAction);
    String pkgPath = basePkg.replace('.', '/');
    ActionTemplate template = actionTemplate(pageName, normalizedAction);

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
        %s
            );
          }
        }
        """.formatted(basePkg, basePkg, pClass, pClass, aClass, normalizedAction, methodName, template.interactions());

    String relativePath = "src/main/java/" + pkgPath + "/action/" + aClass + ".java";
    String featureStep = "When user do \"%s\" in \"%s\" page with parameter\n%s"
        .formatted(normalizedAction, pageName.toLowerCase(Locale.ROOT), template.featureTable(pageName));

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

  private String normalizeActionName(String actionName) {
    return actionName.toLowerCase(Locale.ROOT)
        .replaceAll("\\b(valid|invalid|successful|failed|positive|negative)\\s+(?=credentials?\\b)", "")
        .replaceAll("\\s+", " ")
        .strip();
  }

  private String inferPageName(String actionName) {
    String lower = actionName.toLowerCase(Locale.ROOT);
    if (lower.contains("login") || lower.contains("credential")) return "login";
    if (lower.contains("checkout")) return "checkout";
    if (lower.contains("cart")) return "cart";
    String first = lower.replaceAll("[^a-z0-9]+", " ").strip().split("\\s+")[0];
    return first.isBlank() ? "sample" : first;
  }

  private String pageLocators(String pageName) {
    if (pageName.contains("login") || pageName.contains("auth")) {
      return """
          private static final Locator USERNAME_FIELD = Locator.id("user-name");
          private static final Locator PASSWORD_FIELD = Locator.id("password");
          private static final Locator BUTTON_LOGIN = Locator.id("login-button");
          private static final Locator ERROR_MESSAGE = Locator.css("[data-test='error']");
      """.stripTrailing();
    }
    if (pageName.contains("search")) {
      return """
          private static final Locator SEARCH_INPUT = Locator.css("[data-test='search-input']");
          private static final Locator BUTTON_SEARCH = Locator.css("[data-test='search-submit']");
          private static final Locator SEARCH_RESULTS = Locator.css("[data-test='search-results']");
          private static final Locator ERROR_MESSAGE = Locator.css("[data-test='error-message']");
      """.stripTrailing();
    }
    if (pageName.contains("checkout")) {
      return """
          private static final Locator BUTTON_CHECKOUT = Locator.css("[data-test='checkout']");
          private static final Locator ORDER_SUMMARY = Locator.css("[data-test='order-summary']");
          private static final Locator SUCCESS_MESSAGE = Locator.css("[data-test='success-message']");
          private static final Locator ERROR_MESSAGE = Locator.css("[data-test='error-message']");
      """.stripTrailing();
    }
    return """
          private static final Locator PRIMARY_ACTION = Locator.css("[data-test='primary-action']");
          private static final Locator SUCCESS_MESSAGE = Locator.css("[data-test='success-message']");
          private static final Locator ERROR_MESSAGE = Locator.css("[data-test='error-message']");
      """.stripTrailing();
  }

  private ActionTemplate actionTemplate(String pageName, String actionName) {
    String lower = actionName.toLowerCase(Locale.ROOT);
    if (lower.contains("login") || lower.contains("credential")) {
      return new ActionTemplate(
          """
              Enter.text(String.valueOf(params.get("username"))).into("username field"),
              Enter.text(String.valueOf(params.get("password"))).into("password field"),
              Click.on("button login")
          """.stripTrailing(),
          List.of("username", "password"),
          List.of("properties(test.user.username)", "properties(test.user.password)"));
    }
    if (lower.contains("search")) {
      return new ActionTemplate(
          """
              Enter.text(String.valueOf(params.get("query"))).into("search input"),
              Click.on("button search")
          """.stripTrailing(),
          List.of("query"),
          List.of("properties(test." + pageName.toLowerCase(Locale.ROOT) + ".query)"));
    }
    return new ActionTemplate(
        "    Click.on(\"primary action\")",
        List.of("value"),
        List.of("properties(test." + pageName.toLowerCase(Locale.ROOT) + ".value)"));
  }

  private record ActionTemplate(String interactions, List<String> columns, List<String> values) {
    String featureTable(String pageName) {
      int width = columns.stream().mapToInt(String::length).max().orElse(5) + 2;
      StringBuilder header = new StringBuilder("  |");
      StringBuilder row = new StringBuilder("  |");
      for (int i = 0; i < columns.size(); i++) {
        int colWidth = Math.max(width, values.get(i).length() + 2);
        header.append(" ").append(pad(columns.get(i), colWidth - 1)).append("|");
        row.append(" ").append(pad(values.get(i), colWidth - 1)).append("|");
      }
      return header.append("\n").append(row).toString();
    }

    private static String pad(String value, int length) {
      if (value.length() >= length) return value + " ";
      return value + " ".repeat(length - value.length());
    }
  }
}
