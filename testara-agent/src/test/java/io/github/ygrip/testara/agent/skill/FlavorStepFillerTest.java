package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlavorStepFillerTest {

  @Test
  void fillsParametersInOrderWithTypedValues() {
    FlavorEntry entry = entry("{actor} prepare pathParam for {word} with value {string}", "actor", "word", "string");

    assertEquals("[api] prepare pathParam for id with value \"properties(test.order.id)\"",
        FlavorStepFiller.fill(entry, "[api]", "id", "properties(test.order.id)"));
  }

  @Test
  void fillsIdentityParametersAndSingleQuotesJson() {
    FlavorEntry entry = entry("{sql} prepare query with value {string}", "sql", "string");

    assertEquals("[sql] prepare query with value '{\"id\":1}'",
        FlavorStepFiller.fill(entry, null, "{\"id\":1}"));
  }

  @Test
  void rejectsValuesThatDoNotFitTheParameterType() {
    FlavorEntry status = entry("{actor} response statusCode should be {int}", "actor", "int");
    FlavorEntry alias = entry("{actor} using service with alias {word}", "actor", "word");

    assertThrows(IllegalArgumentException.class, () -> FlavorStepFiller.fill(status, "[api]", "OK"));
    assertThrows(IllegalArgumentException.class, () -> FlavorStepFiller.fill(alias, "[api]", "two words"));
    assertThrows(IllegalArgumentException.class, () -> FlavorStepFiller.fill(alias, "[api]"));
    assertThrows(IllegalArgumentException.class, () -> FlavorStepFiller.fill(alias, "[api]", "a", "b"));
  }

  private FlavorEntry entry(String expression, String... types) {
    return new FlavorEntry("api", "Given", expression, "", "", "testara-api-cucumber", "ApiBaseSteps", List.of(types));
  }
}
