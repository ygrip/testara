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
          ## UIBaseSteps — exact step patterns (use these; do NOT invent variants)
          Given user using chrome in desktop           # chrome|firefox|safari|edge; NOT "web"; desktop|mobile|android|ios
          When  user open "login" page                 # navigates to web.page.desktop.login.url
          Then  user is in "login" page                # REQUIRED after open — registers active page context
          When  user do "action name" in "page" page with parameter
                |key|value|
                | field | value |
          When  user click the "button login"          # element = Locator field SCREAMING_SNAKE -> "lower spaced"
          When  user type value "text" to "element name"
          When  user enter value "text" on "element name"
          Then  user should see "element name" is displayed
          Then  user should see "element name" is not displayed
          Then  user element "element name" should contains text "text"
          Then  user see that
                | actual        | validation | expectation |
                | error message | DISPLAYED  | true        |

          ## UserAction — correct imports
          import io.github.ygrip.testara.ui.model.Action;        // @Action
          import io.github.ygrip.testara.ui.model.OnPage;         // @OnPage
          import io.github.ygrip.testara.ui.executor.UserAction;  // base class
          import io.github.ygrip.testara.ui.interaction.Click;
          import io.github.ygrip.testara.ui.interaction.Enter;
          import io.github.ygrip.testara.ui.interaction.Clear;
          import io.github.ygrip.testara.ui.interaction.SeeThat;
          import io.github.ygrip.testara.ui.interaction.WaitUntil;
          import io.github.ygrip.testara.ui.interaction.SelectOption;
          import io.github.ygrip.testara.ui.interaction.Navigate;

          ## Interactions inside attemptsTo(...)
          Enter.text("value").into("element name")    Click.on("element name")
          Clear.field("element name")                  WaitUntil.visible("element name")
          SelectOption.from("elem").byVisibleText("t") SeeThat.visible("element name")
          SeeThat.containsText("t").on("element name") Navigate.to(NamedPage.of("page"))
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
        web.page.desktop.login.url=${APP_WEB_LOGIN_URL:http://localhost:3000/login}
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
          |key|value|
          | username | properties(test.user.username) |
          | password | properties(test.user.password) |
        ```

        Generate one top-level UserAction class per page/action file under `src/main/java/{basePackage}/action`.
        Do not wrap multiple page actions in an outer class with nested static UserAction classes; nested actions are not the Testara pattern.
        If an action is intentionally shared across pages, keep it top-level and use
        `@Action(value = "action name", allowAnonymousCall = true)`.
        """;
  }

  private String generatePage(String pageName, String engine, String basePkg, Path root, boolean write,
      boolean concise) {
    if (pageName == null) pageName = "sample";
    String pName = toPropertyKey(pageName);
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
         * URL configured via: web.page.desktop.%s.url=${APP_WEB_%s_URL:http://localhost:3000/%s}
         * Element names are resolved from Locator field names, e.g. USERNAME_FIELD -> "username field".
         */
        @Page(name = "%s", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
        public class %s extends %s {
        %s
        }
        """.formatted(basePkg, pageBaseImport, pageName, pName, toEnvKey(pName), pName, pName, pClass, pageBaseClass, locators);

    String propEntry = "web.page.desktop." + pName + ".url=${APP_WEB_" + toEnvKey(pName)
        + "_URL:http://localhost:3000/" + pName + "}";
    String relativePath = "src/main/java/" + pkgPath + "/page/" + pClass + ".java";
    boolean hasTodo = locators.contains("TODO");
    String todoWarning = hasTodo
        ? "\nnote: replace TODO selectors with real DOM selectors before generating features that reference this page's elements."
        : "";

    if (write) {
      try {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source, StandardCharsets.UTF_8);
        return concise ? "written: " + relativePath + "\nadd to application.properties:\n" + propEntry + todoWarning
            : "## Page Written\n\n`" + relativePath + "`\n\n```java\n" + source + "```\n\n**Add to application.properties:**\n```properties\n" + propEntry + "\n```\n" + (hasTodo ? "\n> **Next:** replace `TODO` selectors with actual CSS/XPath selectors from the target app before using UI assertion steps.\n" : "");
      } catch (IOException e) {
        return "Error: " + e.getMessage();
      }
    }
    if (concise) {
      return "file_path: " + relativePath + "\n"
          + "add_to_application.properties:\n" + propEntry + todoWarning + "\n"
          + "```java\n" + source.strip() + "\n```\n";
    }
    return "## Page: " + pClass + "\n\n**Path:** `" + relativePath + "`\n\n**application.properties:**\n```properties\n" + propEntry + "\n```\n\n```java\n" + source + "```\n"
        + (hasTodo ? "\n> **Next:** replace `TODO` selectors with real DOM selectors before using UI assertion steps.\n" : "")
        + "\n> Write the source block to `" + relativePath + "` and add the properties entry above.";
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
    String pageKey = toPropertyKey(pageName);
    String featureStep = "When user do \"%s\" in \"%s\" page with parameter\n%s"
        .formatted(normalizedAction, pageKey, template.featureTable(pageKey));

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
    if (concise) {
      return "file_path: " + relativePath + "\n"
          + "feature_step:\n" + featureStep + "\n"
          + "```java\n" + source.strip() + "\n```\n";
    }
    return "## UserAction: " + aClass + "\n\n**Path:** `" + relativePath + "`\n\n```java\n" + source + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n"
        + "\n> Write the source block to `" + relativePath + "`.";
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
          selenium.driver.headless=false
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
    // Unknown page type — emit empty stubs. The agent must supply real selectors from the target app DOM.
    // Field name → step alias rule: FIELD_NAME → "field name" (SCREAMING_SNAKE_CASE → lower spaced).
    // Do not generate "user should see 'X' is displayed" steps until these are replaced.
    return """
          // TODO: replace with actual selectors from the target app's DOM inspector.
          // Each Locator field name becomes a step alias: e.g. SUBMIT_BUTTON -> "submit button"
          private static final Locator SUBMIT_BUTTON = Locator.css("TODO");
          private static final Locator SUCCESS_MESSAGE = Locator.css("TODO");
          private static final Locator ERROR_MESSAGE = Locator.css("TODO");
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
          List.of("properties(test." + toPropertyKey(pageName) + ".query)"));
    }
    return new ActionTemplate(
        "    Click.on(\"primary action\")",
        List.of("value"),
        List.of("properties(test." + toPropertyKey(pageName) + ".value)"));
  }

  private String toPropertyKey(String value) {
    return value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private String toEnvKey(String value) {
    return value.toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "_")
        .replaceAll("^_|_$", "");
  }

  private record ActionTemplate(String interactions, List<String> columns, List<String> values) {
    String featureTable(String pageName) {
      int keyWidth = columns.stream().mapToInt(String::length).max().orElse(3);
      int valueWidth = values.stream().mapToInt(String::length).max().orElse(5);
      StringBuilder table = new StringBuilder("  |key|value|");
      for (int i = 0; i < columns.size(); i++) {
        table.append("\n")
            .append("  | ")
            .append(pad(columns.get(i), keyWidth))
            .append(" | ")
            .append(pad(values.get(i), valueWidth))
            .append(" |");
      }
      return table.toString();
    }

    private static String pad(String value, int length) {
      if (value.length() >= length) return value + " ";
      return value + " ".repeat(length - value.length());
    }
  }
}
