package io.github.ygrip.testara.engine.option;

import io.github.ygrip.testara.engine.testsupport.MapConfigurationParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraCucumberEngineOptionsTests {

  @Test
  void everyScenarioIsValidWithoutTagFilter() {
    TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(new MapConfigurationParameters(Map.of()));

    assertTrue(options.isValidTags(List.of("@api", "@sample")));
    assertTrue(options.isValidTags(List.of()));
  }

  @Test
  void tagFilterSelectsMatchingScenarios() {
    TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(
        new MapConfigurationParameters(Map.of("cucumber.filter.tags", "@api and not @slow")));

    assertTrue(options.isValidTags(List.of("@api")));
    assertFalse(options.isValidTags(List.of("@api", "@slow")));
    assertFalse(options.isValidTags(List.of("@ui")));
  }
}
