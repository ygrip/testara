package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.DriverIndex;
import io.github.ygrip.testara.agent.index.ExamplesIndex;
import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.ScenarioType;
import io.github.ygrip.testara.agent.index.StepIndex;
import io.github.ygrip.testara.agent.index.TagIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression coverage for the warm-cache round-trip. A cache hit must restore features,
 * tags, drivers and resource roots (previously dropped), and resolve projectRoot to the
 * real project root rather than two directories above it.
 */
class ProfileSerializerTest {

  @TempDir
  Path projectRoot;

  @Test
  void roundTripPreservesFeaturesTagsDriversRootsAndProjectRoot() {
    Path cacheFile = projectRoot.resolve(".testara-agent/knowledge/profile-cache.json");

    Path featurePath = projectRoot.resolve("src/test/resources/features/login.feature");
    StepIndex step = new StepIndex("Given", "user opens the login page");
    ScenarioIndex scenario = new ScenarioIndex("successful login", ScenarioType.SCENARIO,
        List.of("@smoke"), List.of(step), List.of(new ExamplesIndex(List.of("username"), 3)));
    FeatureIndex feature = new FeatureIndex(featurePath, "Login", List.of("@ui"),
        List.of(scenario), List.of());
    TagIndex tag = new TagIndex("@smoke", 1, 1, List.of(featurePath), List.of("successful login"));
    DriverIndex driver = new DriverIndex("chrome", "SeleniumEngine", List.of("desktop"),
        "chrome", projectRoot.resolve("Drivers.java"), "com.example.Drivers");

    TestaraProjectProfile original = new TestaraProjectProfile(
        projectRoot, BuildTool.MAVEN, "21", List.of("mod-a"),
        List.of(projectRoot.resolve("features")), List.of(projectRoot.resolve("specs")),
        List.of(projectRoot.resolve("validations")),
        List.of(feature), List.of(), List.of(), List.of(), List.of(driver), List.of(tag),
        Map.of("web.page.desktop.login.url", "https://example.test/login"), Map.of(),
        List.of(), List.of());

    ProfileSerializer.save(cacheFile, original);
    TestaraProjectProfile restored = ProfileSerializer.load(cacheFile);

    assertNotNull(restored, "cache should restore a profile");
    assertEquals(projectRoot, restored.projectRoot(), "projectRoot must resolve to the real root");
    assertEquals(1, restored.features().size(), "features must survive the round-trip");
    assertEquals("Login", restored.features().get(0).featureName());
    assertEquals(1, restored.features().get(0).scenarios().size());
    assertEquals(1, restored.totalScenarios());
    assertEquals(1, restored.tags().size(), "tags must survive the round-trip");
    assertEquals("@smoke", restored.tags().get(0).tag());
    assertEquals(1, restored.drivers().size(), "drivers must survive the round-trip");
    assertEquals("chrome", restored.drivers().get(0).name());
    assertEquals(1, restored.featureRoots().size(), "resource roots must survive the round-trip");
    assertEquals("https://example.test/login",
        restored.properties().get("web.page.desktop.login.url"));
  }
}
