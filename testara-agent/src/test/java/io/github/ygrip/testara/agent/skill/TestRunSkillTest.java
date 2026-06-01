package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.ScenarioType;
import io.github.ygrip.testara.agent.index.TagIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void resolvesScenarioTextToNarrowTagExpression() {
    String output = new TestRunSkill().execute("run complete purchase flow", context(profileWithSaucedemo()));

    assertTrue(output.contains("**Resolved tag expression:** `@ui and @saucedemo and @regression and @smoke`"));
    assertTrue(output.contains("**Matched scenarios:** 1"));
    assertFalse(output.contains(" or "));
    assertFalse(output.contains("needs_input"));
  }

  @Test
  void keepsExplicitTagInputDirect() {
    String output = new TestRunSkill().execute("run @smoke", context(profileWithSaucedemo()));

    assertTrue(output.contains("**Resolved tag expression:** `@smoke`"));
    assertTrue(output.contains("**Matched scenarios:** 1"));
  }

  @Test
  void asksForInputWhenProjectHasNoRunnableContext() {
    String output = new TestRunSkill().execute("run checkout", context(emptyProfile()));

    assertTrue(output.contains("needs_input: test_run_filter"));
    assertTrue(output.contains("feature name, or scenario name"));
    assertTrue(output.contains("indexed-features: 0 | indexed-scenarios: 0"));
  }

  private AgentContext context(TestaraProjectProfile profile) {
    return new AgentContext(projectRoot, profile, AgentMode.READ_ONLY, null, Map.of("dryRun", "true"));
  }

  private TestaraProjectProfile profileWithSaucedemo() {
    ScenarioIndex purchase = new ScenarioIndex("Complete purchase flow - login, add to cart, and checkout",
        ScenarioType.SCENARIO, List.of("@P1", "@positive", "@smoke"), List.of(), List.of());
    FeatureIndex feature = new FeatureIndex(projectRoot.resolve("src/test/resources/features/saucedemo/e2e.feature"),
        "SauceDemo E2E - Login, Add to Cart, and Checkout",
        List.of("@ui", "@saucedemo", "@regression"), List.of(purchase), List.of());
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(projectRoot.resolve("src/test/resources/features")), List.of(), List.of(),
        List.of(feature), List.of(), List.of(), List.of(), List.of(),
        List.of(new TagIndex("@ui", 1, 1, List.of(feature.path()), List.of(purchase.name())),
            new TagIndex("@saucedemo", 1, 1, List.of(feature.path()), List.of(purchase.name())),
            new TagIndex("@regression", 1, 1, List.of(feature.path()), List.of(purchase.name())),
            new TagIndex("@smoke", 1, 1, List.of(feature.path()), List.of(purchase.name()))),
        Map.of(), Map.of(), List.of(), List.of());
  }

  private TestaraProjectProfile emptyProfile() {
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), List.of(), List.of());
  }
}
