package io.github.ygrip.testara.agent.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputValidatorTest {

  @Test
  void validatesFeatureContent() {
    String validFeature = """
        Feature: Login
          Scenario: Successful login
            Given a user
            When they login
            Then it works""";

    var result = OutputValidator.validateFeature(validFeature);
    assertTrue(result.valid(), "Valid feature should pass");
  }

  @Test
  void rejectsEmptyFeature() {
    var result = OutputValidator.validateFeature("");
    assertFalse(result.valid());
    assertTrue(result.errors().get(0).contains("Empty"));
  }

  @Test
  void rejectsFeatureMissingHeader() {
    var result = OutputValidator.validateFeature("Scenario: Missing feature header");
    assertFalse(result.valid());
    assertTrue(result.errors().get(0).contains("Missing 'Feature:'"));
  }

  @Test
  void validatesJavaCommandSource() {
    String validCmd = """
        package com.test;
        @CommandTag(command = "my-cmd")
        public class MyCmd implements CommandLogic<String> {
          public String execute(List<Object> p) { return "ok"; }
        }""";

    var result = OutputValidator.validateJavaSource(validCmd, true, false);
    assertTrue(result.valid(), "Valid command class should pass");
  }

  @Test
  void rejectsJavaCommandMissingAnnotation() {
    String missingAnnotation = """
        package com.test;
        public class MyCmd implements CommandLogic<String> {
          public String execute(List<Object> p) { return "ok"; }
        }""";

    var result = OutputValidator.validateJavaSource(missingAnnotation, true, false);
    assertFalse(result.valid());
    assertTrue(result.errors().get(0).contains("@CommandTag"));
  }

  @Test
  void validatesValidatorSource() {
    String validValidator = """
        package com.test;
        @ValidationTag(command = "my-val")
        public class MyVal extends ValidatorLogic<Object, Object> {
          public boolean validate() { return true; }
        }""";

    var result = OutputValidator.validateJavaSource(validValidator, false, true);
    assertTrue(result.valid(), "Valid validator should pass");
  }

  @Test
  void rejectsValidatorMissingAnnotation() {
    String missing = "package com.test; public class MyVal { }";
    var result = OutputValidator.validateJavaSource(missing, false, true);
    assertFalse(result.valid());
    assertTrue(result.errors().get(0).contains("@ValidationTag"));
  }

  @Test
  void validatesJson() {
    var result = OutputValidator.validateJson("{\"key\": \"value\"}");
    assertTrue(result.valid());
  }

  @Test
  void rejectsInvalidJson() {
    var result = OutputValidator.validateJson("not json");
    assertFalse(result.valid());
    assertTrue(result.errors().get(0).contains("JSON"));
  }

  @Test
  void rejectsEmptyJson() {
    var result = OutputValidator.validateJson("");
    assertFalse(result.valid());
  }
}
