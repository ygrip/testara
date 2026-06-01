package io.github.ygrip.testara.agent.skill.run;

import io.github.ygrip.testara.agent.index.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TagExpressionResolverTest {

  private final TagExpressionResolver resolver = new TagExpressionResolver();

  private TestaraProjectProfile profileWithTags(String... tags) {
    List<TagIndex> tagIndices = List.of(tags).stream()
        .map(t -> new TagIndex(t, 1, 3, List.of(), List.of()))
        .toList();
    return new TestaraProjectProfile(
        Path.of("."), BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), tagIndices,
        Map.of(), Map.of(), List.of(), List.of());
  }

  @Test
  void resolvesExplicitTags() {
    String result = resolver.resolve("run @api and @smoke tests",
        profileWithTags("@api", "@smoke", "@regression"));
    assertTrue(result.contains("@api"));
    assertTrue(result.contains("@smoke"));
  }

  @Test
  void resolvesNaturalLanguageToAliases() {
    String result = resolver.resolve("run smoke tests",
        profileWithTags("@smoke", "@api"));
    assertTrue(result.contains("@smoke"), "Should resolve 'smoke' to @smoke");
  }

  @Test
  void resolvesNotClauses() {
    String result = resolver.resolve("run api tests except slow",
        profileWithTags("@api", "@slow"));
    assertTrue(result.contains("@api"), "Should include @api");
    assertTrue(result.contains("not @slow"), "Should exclude @slow");
  }

  @Test
  void resolvesPriorityAliases() {
    String result = resolver.resolve("run critical tests",
        profileWithTags("@P0", "@critical"));
    assertTrue(result.contains("@P0"), "Should resolve 'critical' to @P0");
  }

  @Test
  void returnsEmptyForUnresolvable() {
    String result = resolver.resolve("run nonexistent tests",
        profileWithTags("@api", "@smoke"));
    assertEquals("", result, "Should return empty for unresolvable intent");
  }

  @Test
  void resolvesIndexedTags() {
    String result = resolver.resolve("run payment tests",
        profileWithTags("@payment", "@api", "@smoke"));
    assertTrue(result.contains("@payment"), "Should match indexed tag");
  }

  @Test
  void handlesEmptyInput() {
    assertEquals("", resolver.resolve("", profileWithTags("@smoke")));
    assertEquals("", resolver.resolve(null, profileWithTags("@smoke")));
  }

  @Test
  void resolvesFlakyExclusion() {
    String result = resolver.resolve("run api tests except flaky",
        profileWithTags("@api", "@flaky", "@smoke"));
    assertTrue(result.contains("@api"));
    assertTrue(result.contains("not @flaky"));
  }

  @Test
  void resolvesNaturalLanguageFromFeatureAndScenarioText() {
    ScenarioIndex scenario = new ScenarioIndex("User logs in to SauceDemo",
        ScenarioType.SCENARIO, List.of("@positive"), List.of(), List.of());
    FeatureIndex feature = new FeatureIndex(Path.of("login.feature"), "SauceDemo login",
        List.of("@ui", "@saucedemo"), List.of(scenario), List.of());
    TestaraProjectProfile profile = new TestaraProjectProfile(
        Path.of("."), BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(feature), List.of(), List.of(), List.of(), List.of(),
        List.of(new TagIndex("@ui", 1, 1, List.of(), List.of()),
            new TagIndex("@saucedemo", 1, 1, List.of(), List.of())),
        Map.of(), Map.of(), List.of(), List.of());

    String result = resolver.resolve("run the saucedemo login tests in dry-run mode", profile);

    assertTrue(result.contains("@saucedemo"));
    assertEquals(1, resolver.countMatching(result, profile));
  }

  @Test
  void usesConjunctionForSpecificScenarioText() {
    ScenarioIndex purchase = new ScenarioIndex("Complete purchase flow - login, add to cart, and checkout",
        ScenarioType.SCENARIO, List.of("@P1", "@positive", "@smoke"), List.of(), List.of());
    FeatureIndex feature = new FeatureIndex(Path.of("saucedemo-e2e.feature"),
        "SauceDemo E2E - Login, Add to Cart, and Checkout",
        List.of("@ui", "@saucedemo", "@regression"), List.of(purchase), List.of());
    TestaraProjectProfile profile = new TestaraProjectProfile(
        Path.of("."), BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(feature), List.of(), List.of(), List.of(), List.of(),
        List.of(new TagIndex("@ui", 1, 1, List.of(), List.of()),
            new TagIndex("@saucedemo", 1, 1, List.of(), List.of()),
            new TagIndex("@regression", 1, 1, List.of(), List.of()),
            new TagIndex("@smoke", 1, 1, List.of(), List.of())),
        Map.of(), Map.of(), List.of(), List.of());

    String result = resolver.resolve("run complete purchase flow", profile);

    assertTrue(result.contains("@ui"));
    assertTrue(result.contains("@saucedemo"));
    assertTrue(result.contains("@smoke"));
    assertFalse(result.contains(" or "), "Specific text should narrow with AND, not broaden with OR");
    assertEquals(1, resolver.countMatching(result, profile));
  }

  @Test
  void defaultsToAndForMultipleNaturalTagsUnlessUserSaysOr() {
    String result = resolver.resolve("run api smoke tests",
        profileWithTags("@api", "@smoke", "@regression"));
    String orResult = resolver.resolve("run api or ui tests",
        profileWithTags("@api", "@ui", "@regression"));

    assertEquals("@api and @smoke", result);
    assertEquals("(@api or @ui)", orResult);
  }
}
