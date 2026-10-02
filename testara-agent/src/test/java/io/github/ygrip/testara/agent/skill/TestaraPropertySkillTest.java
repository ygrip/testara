package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraPropertySkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void listRedactsSecretValues() throws IOException {
    Path config = projectRoot.resolve("src/test/resources/configuration.properties");
    Files.createDirectories(config.getParent());
    Files.writeString(config, "sql.service.orderDb.password=hunter2\nsql.service.orderDb.host-name=db.local\n");

    String output = new TestaraPropertySkill().execute(new TestaraPropertySkill.Input("list", null, null, null),
        new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of()));

    assertFalse(output.contains("hunter2"), output);
    assertTrue(output.contains("sql.service.orderDb.password=[REDACTED]"), output);
    assertTrue(output.contains("sql.service.orderDb.host-name=db.local"), output);
  }

  @Test
  void uiBlockUsesTheProjectPackageInsteadOfAPlaceholder() {
    String output = new TestaraPropertySkill().execute(new TestaraPropertySkill.Input("generate", "login", null, "ui"),
        new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of("package", "com.acme.qa")));

    assertFalse(output.contains("{basePackage}"), output);
    assertTrue(output.contains("selenium.driver.page-scan-locations=io.github.ygrip.testara,com.acme.qa"), output);
  }
}
