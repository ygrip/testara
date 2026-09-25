package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCommandSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void generatedCommandDefaultsToTheProjectCommandPackageUnderMainJava() {
    String output = new TestCommandSkill().execute("customer code", context(Map.of(
        "commandScanPackages", "io.github.ygrip.testara,com.acme.qa.command")));

    assertTrue(output.contains("package com.acme.qa.command;"), output);
    assertTrue(output.contains("placement: src/main/java/com/acme/qa/command/CustomerCodeCommand.java"), output);
    assertFalse(output.contains("io.github.ygrip.testara.command;"), output);
  }

  @Test
  void generatedValidationUsesUpperSnakeNameAndProjectPackage() {
    String output = new TestValidationSkill().execute("valid order status", context(Map.of(
        "validationScanPackages", "io.github.ygrip.testara,com.acme.qa.validation")));

    assertTrue(output.contains("@ValidationTag(command = \"VALID_ORDER_STATUS\")"), output);
    assertTrue(output.contains("package com.acme.qa.validation;"), output);
    assertTrue(output.contains("placement: src/main/java/com/acme/qa/validation/"), output);
  }

  private AgentContext context(Map<String, String> properties) {
    TestaraProjectProfile profile = new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        properties, Map.of(), List.of(), List.of());
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }
}
