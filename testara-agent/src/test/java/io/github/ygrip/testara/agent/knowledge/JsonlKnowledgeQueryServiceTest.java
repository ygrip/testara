package io.github.ygrip.testara.agent.knowledge;

import io.github.ygrip.testara.agent.index.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonlKnowledgeQueryServiceTest {

  @Test
  void queriesProfileInsteadOfReturningEmptyStubs() {
    ScenarioIndex scenario = new ScenarioIndex(
        "Checkout succeeds", ScenarioType.SCENARIO,
        List.of("@smoke"), List.of(new StepIndex("Given", "a cart")), List.of());
    FeatureIndex feature = new FeatureIndex(
        Path.of("checkout.feature"), "Checkout", List.of("@api"),
        List.of(scenario), List.of());
    TestaraProjectProfile profile = new TestaraProjectProfile(
        Path.of("."), BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(), List.of(feature),
        List.of(new StepDefinitionIndex("Given", "a cart", Path.of("Steps.java"), "Steps", "")),
        List.of(new CommandIndex("token", List.of("auth"), "String", false, Path.of("Token.java"), "Token")),
        List.of(new ValidationIndex("ok", List.of("success"), "Object", "Object", false, Path.of("Ok.java"), "Ok")),
        List.of(),
        List.of(new TagIndex("@api", 1, 1, 1, List.of(Path.of("checkout.feature")), List.of("Checkout succeeds"))),
        Map.of(), Map.of(), List.of(), List.of());

    JsonlKnowledgeQueryService service = new JsonlKnowledgeQueryService(profile);

    assertEquals(1, service.findFeatures(KnowledgeQuery.fromText("Checkout")).size());
    assertEquals(1, service.findScenarios(KnowledgeQuery.fromTag("@api"), profile.features()).size());
    assertEquals(1, service.findStepDefinitions(KnowledgeQuery.fromText("cart")).size());
    assertEquals(1, service.findTags(KnowledgeQuery.fromTag("@api")).size());
    assertEquals(1, service.findCommands(KnowledgeQuery.fromText("token")).size());
    assertEquals(1, service.findValidations(KnowledgeQuery.fromText("success")).size());
  }
}
