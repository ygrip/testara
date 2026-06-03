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

class TestaraIndexSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void returnsCompactUiCatalogAndRedactsPropertyValues() throws IOException {
    Path page = projectRoot.resolve("src/main/java/com/acme/page/LoginPage.java");
    Files.createDirectories(page.getParent());
    Files.writeString(page, """
        package com.acme.page;

        import io.github.ygrip.testara.ui.model.Locator;
        import io.github.ygrip.testara.ui.model.Page;

        @Page(name = "login", url = "")
        public class LoginPage {
          private static final Locator USERNAME_FIELD = Locator.id("user-name");
          private static final Locator BUTTON_LOGIN = Locator.css("[data-test='login']");
        }
        """);

    Path action = projectRoot.resolve("src/main/java/com/acme/action/LoginActions.java");
    Files.createDirectories(action.getParent());
    Files.writeString(action, """
        package com.acme.action;

        import io.github.ygrip.testara.ui.model.Action;
        import java.util.Map;

        public class LoginActions {
          @Action("login with valid credentials")
          public void loginWithValidCredentials(Map<String, Object> params) {}
        }
        """);

    Path properties = projectRoot.resolve("src/test/resources/application.properties");
    Files.createDirectories(properties.getParent());
    Files.writeString(properties, """
        saucedemo.username=standard_user
        saucedemo.password=secret_sauce
        """);

    String output = new TestaraIndexSkill().execute(null, context());

    assertTrue(output.contains("action: \"login with valid credentials\""));
    assertTrue(output.contains("method: loginWithValidCredentials"));
    assertTrue(output.contains("name: login"));
    assertTrue(output.contains("USERNAME_FIELD: Locator.id(\"user-name\")"));
    assertTrue(output.contains("saucedemo.username"));
    assertTrue(output.contains("saucedemo.password"));
    assertTrue(output.contains("property-values: redacted"));
    assertFalse(output.contains("standard_user"));
    assertFalse(output.contains("secret_sauce"));
  }

  private AgentContext context() {
    TestaraProjectProfile profile = new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }
}
