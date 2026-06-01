package io.github.ygrip.testara.agent.catalog;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.StepDefinitionIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepLinkerTest {

  @Test
  void linksUiBaseStepsUsingCucumberExpressions() {
    String feature = """
        Scenario: login
          Given user using chrome in desktop
          When user type value "properties(test.user.username)" to "username field" in the "login" page
          And user click the "button login" in the "login" page
          Then user should see "success message" is displayed
        """;
    List<FlavorEntry> catalog = List.of(
        flavor("Given", "{actor} using {word} in {devices}"),
        flavor("When", "{actor} type value {string} to {string} in the {string} page"),
        flavor("When", "{actor} click the {string} in the {string} page"),
        flavor("Then", "{actor} should see {string} is {displayedOrNotDisplayed}"));

    var links = StepLinker.linkFeature(feature, catalog, List.of());

    assertEquals(4, links.size());
    assertTrue(links.stream().allMatch(StepLinker.Link::matched), () -> links.toString());
    assertTrue(links.stream().allMatch(link -> link.source() == StepLinker.Source.BUILT_IN));
  }

  @Test
  void linksProjectStepDefinitionsAndReportsUnknownSteps() {
    String feature = """
        Scenario: project step
          When user has a project-only step
          Then user sees magic text
        """;
    List<StepDefinitionIndex> projectSteps = List.of(new StepDefinitionIndex(
        "When", "user has a project-only step", Path.of("src/test/java/Steps.java"),
        "Steps", "projectOnly"));

    var links = StepLinker.linkFeature(feature, List.of(), projectSteps);

    assertEquals(StepLinker.Source.PROJECT, links.get(0).source());
    assertEquals(StepLinker.Source.UNMATCHED, links.get(1).source());
  }

  private FlavorEntry flavor(String keyword, String expression) {
    return new FlavorEntry("ui", keyword, expression, "", expression,
        "testara-ui-cucumber", "UIBaseSteps");
  }
}
