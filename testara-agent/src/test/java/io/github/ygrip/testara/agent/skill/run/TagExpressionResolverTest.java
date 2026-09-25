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
  @Test
  void preservesExplicitExpressionPrecedence() {
    String expression = "@api or @ui and not @slow";
    assertEquals(expression, resolver.resolve(expression,
        profileWithTags("@api", "@ui", "@slow")));
  }

  @Test
  void doesNotTurnExplicitNegativeTagPositive() {
    String result = resolver.resolve("run @api except @slow",
        profileWithTags("@api", "@slow"));
    assertEquals("@api and not @slow", result);
  }

  @Test
  void exceptNegatesTheWholeTrailingExpression() {
    TestaraProjectProfile profile = profileWithTags("@smoke", "@slow", "@flaky");

    assertEquals("@smoke and not (@slow or @flaky)", resolver.resolve("@smoke except @slow or @flaky", profile));
    assertEquals("@smoke and not (@slow or @flaky)", resolver.resolve("run smoke except slow and flaky", profile));
    assertEquals("not @slow", resolver.resolve("run everything except slow", profile));
  }

  @Test
  void keepsNegativeTagCaseAsTyped() {
    assertEquals("@api and not @WIP", resolver.resolve("run @api except @WIP",
        profileWithTags("@api", "@WIP")));
  }

  @Test
  void parenthesizesMultiTokenAliases() {
    TagExpressionResolver custom = new TagExpressionResolver(Map.of(
        "checkout", "@cart or @payment",
        "legacy", "(@old or @deprecated)"));
    TestaraProjectProfile profile = profileWithTags("@cart", "@payment", "@smoke", "@old", "@deprecated");

    assertEquals("@smoke and not (@cart or @payment)", custom.resolve("run smoke except checkout", profile));
    assertEquals("(@cart or @payment) and @smoke", custom.resolve("run checkout smoke", profile));
    assertEquals("@smoke and not (@old or @deprecated)", custom.resolve("run smoke except legacy", profile));
  }

  @Test
  void countsOutlinesByExamplesTagsWithCucumberSemantics() {
    ScenarioIndex outline = new ScenarioIndex("login outline", ScenarioType.SCENARIO_OUTLINE, List.of("@login"),
        List.of(), List.of(new ExamplesIndex(List.of("@smoke"), List.of("user"), 2),
            new ExamplesIndex(List.of("@slow"), List.of("user"), 3)));
    FeatureIndex feature = new FeatureIndex(Path.of("login.feature"), "Login", List.of(), List.of(outline), List.of());
    TestaraProjectProfile profile = new TestaraProjectProfile(
        Path.of("."), BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(), List.of(feature),
        List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());

    assertEquals(1, resolver.countMatching("@smoke", profile), "tag only on an Examples block must match");
    assertEquals(1, resolver.countMatching("@login and not @slow", profile), "the @smoke Examples block still runs");
    assertEquals(0, resolver.countMatching("@smoke and @slow", profile), "no single Examples block has both");
    assertTrue(resolver.matches("@slow", feature, outline));
  }

  @Test
  void evaluatesCucumberExpressionPrecedence() {
    ScenarioIndex apiSlow = new ScenarioIndex("api slow", ScenarioType.SCENARIO,
        List.of("@api", "@slow"), List.of(), List.of());
    ScenarioIndex uiFast = new ScenarioIndex("ui fast", ScenarioType.SCENARIO,
        List.of("@ui"), List.of(), List.of());
    FeatureIndex feature = new FeatureIndex(Path.of("selection.feature"), "Selection",
        List.of(), List.of(apiSlow, uiFast), List.of());
    TestaraProjectProfile profile = new TestaraProjectProfile(
        Path.of("."), BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(), List.of(feature),
        List.of(), List.of(), List.of(), List.of(),
        List.of(new TagIndex("@api", 1, 1, List.of(), List.of()),
            new TagIndex("@ui", 1, 1, List.of(), List.of()),
            new TagIndex("@slow", 1, 1, List.of(), List.of())),
        Map.of(), Map.of(), List.of(), List.of());

    assertEquals(2, resolver.countMatching("@api or @ui and not @slow", profile));
    assertEquals(1, resolver.countMatching("(@api or @ui) and not @slow", profile));
  }

}
