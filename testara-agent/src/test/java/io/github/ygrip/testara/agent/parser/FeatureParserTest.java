package io.github.ygrip.testara.agent.parser;

import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.ScenarioType;
import io.github.ygrip.testara.agent.index.StepIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureParserTest {

  private final FeatureParser parser = new FeatureParser();

  @Test
  void parsesSimpleFeature(@TempDir Path tempDir) throws IOException {
    String content = "@api @smoke\nFeature: Login\n\n  @P1 @positive\n  Scenario: Successful login\n    Given the login page is displayed\n    When the user enters valid credentials\n    Then the user should be redirected to the dashboard\n";
    Path file = tempDir.resolve("login.feature");
    Files.writeString(file, content);

    FeatureIndex feature = parser.parse(file);

    assertEquals("Login", feature.featureName());
    assertEquals(1, feature.scenarios().size());
    ScenarioIndex scenario = feature.scenarios().get(0);
    assertEquals("Successful login", scenario.name());
    assertEquals(ScenarioType.SCENARIO, scenario.type());
    assertEquals(3, scenario.steps().size());
    assertEquals("Given", scenario.steps().get(0).keyword());
    assertEquals("When", scenario.steps().get(1).keyword());
    assertEquals("Then", scenario.steps().get(2).keyword());
  }

  @Test
  void parsesScenarioOutlineWithExamples(@TempDir Path tempDir) throws IOException {
    String content = """
        Feature: User Search

          Scenario Outline: Search by criteria
            Given the user is on the search page
            When the user searches for "<query>"
            Then results should contain "<expected>"

            Examples:
              | query | expected |
              | apple | fruit    |
              | car   | vehicle  |""";
    Path file = tempDir.resolve("search.feature");
    Files.writeString(file, content);

    FeatureIndex feature = parser.parse(file);

    assertEquals(1, feature.scenarios().size());
    ScenarioIndex scenario = feature.scenarios().get(0);
    assertEquals(ScenarioType.SCENARIO_OUTLINE, scenario.type());
    assertEquals(1, scenario.examples().size());
    assertEquals(2, scenario.examples().get(0).rowCount());
    assertEquals(List.of("query", "expected"), scenario.examples().get(0).headers());
  }

  @Test
  void parsesBackground(@TempDir Path tempDir) throws IOException {
    String content = """
        Feature: Order Management

          Background:
            Given the API service "order-api" is available
            And a test user exists

          Scenario: Create order
            When the user creates an order
            Then the order should be created""";
    Path file = tempDir.resolve("order.feature");
    Files.writeString(file, content);

    FeatureIndex feature = parser.parse(file);

    assertEquals(2, feature.backgroundSteps().size());
    assertEquals("Given", feature.backgroundSteps().get(0).keyword());
    assertEquals("And", feature.backgroundSteps().get(1).keyword());
  }

  @Test
  void parsesStepDataTable(@TempDir Path tempDir) throws IOException {
    String content = """
        Feature: Bulk User Import

          Scenario: Import users from CSV
            Given the following users exist:
              | name  | email          | role   |
              | John  | john@test.com  | admin  |
              | Jane  | jane@test.com  | viewer |
            When the import is triggered
            Then all users should be created""";
    Path file = tempDir.resolve("bulk.feature");
    Files.writeString(file, content);

    FeatureIndex feature = parser.parse(file);

    assertEquals(1, feature.scenarios().size());
    ScenarioIndex scenario = feature.scenarios().get(0);
    assertEquals(3, scenario.steps().size());

    StepIndex firstStep = scenario.steps().get(0);
    assertEquals("Given", firstStep.keyword());
    assertEquals("the following users exist:", firstStep.text());
    assertFalse(firstStep.dataTable().isEmpty(), "Step should have data table");
    assertEquals(3, firstStep.dataTable().size(), "Data table should have 3 rows");

    // Header row
    assertEquals(List.of("name", "email", "role"), firstStep.dataTable().get(0));
    // Data rows
    assertEquals("John", firstStep.dataTable().get(1).get(0));
    assertEquals("jane@test.com", firstStep.dataTable().get(2).get(1));
  }

  @Test
  void stepWithoutDataTableHasEmptyTable(@TempDir Path tempDir) throws IOException {
    String content = """
        Feature: Simple

          Scenario: No table
            Given a simple step
            When something happens
            Then it works""";
    Path file = tempDir.resolve("simple.feature");
    Files.writeString(file, content);

    FeatureIndex feature = parser.parse(file);
    StepIndex firstStep = feature.scenarios().get(0).steps().get(0);
    assertTrue(firstStep.dataTable().isEmpty(), "Step without data table should have empty list");
  }
}
