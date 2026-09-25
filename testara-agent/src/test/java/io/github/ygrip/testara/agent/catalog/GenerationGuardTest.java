package io.github.ygrip.testara.agent.catalog;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationGuardTest {

  @Test
  void featureGuardReportsStepsThatDoNotLinkToKnownGlue() {
    String feature = """
        Scenario: invalid
          Then user should see "complete header" with text "Thank you for your order!"
        """;
    List<FlavorEntry> catalog = List.of(new FlavorEntry("ui", "Then",
        "{actor} should see {string} is {displayedOrNotDisplayed}", "",
        "display assertion", "testara-ui-cucumber", "UIBaseSteps"));

    var violations = GenerationGuard.validateFeature(feature, catalog, List.of());

    assertTrue(violations.stream().anyMatch(v -> v.rule().equals("STEP")));
    assertTrue(violations.stream().anyMatch(v -> v.line().contains("with text")));
  }
}
