package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import io.github.ygrip.testara.agent.catalog.StepLinker;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlanSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void loginUiPlanUsesExecutableUiBaseSteps() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("login to saucedemo and land on inventory", "ui", "saucedemo", List.of()),
        context());

    assertTrue(output.contains("Given user using chrome in desktop"));
    assertTrue(output.contains("When user open \"login\" page"));
    // RULE 3: 3+ ops → user do "..." in "..." page with parameter + | key | value | DataTable
    assertTrue(output.contains("When user do \"login with credentials\" in \"login\" page with parameter"));
    assertTrue(output.contains("|key|value|"));
    assertTrue(output.contains("| username"));
    assertTrue(output.contains("| password"));
    assertTrue(output.contains("properties(test.user.username)"));
    assertTrue(output.contains("properties(test.user.password)"));
    // Must NOT fall back to individual type/click steps
    assertFalse(output.contains("user type value \"properties(test.user.username)\" to \"username field\""));
    assertFalse(output.contains("user click the \"button login\""));
    assertTrue(output.contains("Then user is in \"inventory\" page"));
    assertTrue(output.contains("Then user should see \"success message\" is displayed"));
    assertTrue(output.contains("Then user should see \"error message\" is displayed"));
    assertFalse(output.contains("# MISSING"));
    assertFalse(output.contains("user using web in desktop"));
  }

  @Test
  void apiPlanLinksEveryStepToRealDefinitionsAndReferencesTheSpecItWrites() throws IOException {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("create order returns success", "api", "order", List.of()), writeContext());

    Path feature = projectRoot.resolve("src/test/resources/features/api/order/create-order-returns-success.feature");
    assertTrue(Files.exists(feature), output);
    String content = Files.readString(feature);
    assertAllStepsLink(content);
    assertFalse(content.contains("\"value\""), content);
    assertFalse(content.contains("alias value"), content);
    assertTrue(content.contains("Given [api] using service with alias order-api"), content);
    assertTrue(content.contains("When [api] process request to \"files/order/request/create-order\""), content);
    assertTrue(content.contains("When [api] process request to \"files/order/request/create-order-invalid\""), content);
    assertTrue(content.contains("Then [api] response statusCode should be 200"), content);
    assertTrue(content.contains("Then [api] response statusCode should be 400"), content);
    assertTrue(content.contains("Then [api] assign previous response data to orderResponse"), content);

    String spec = Files.readString(projectRoot.resolve("src/test/resources/files/order/request/create-order.json"));
    assertTrue(spec.contains("\"specification\": \"order-api\""), spec);
    assertTrue(spec.contains("\"httpMethod\": \"POST\""), spec);
    assertTrue(spec.contains("\"url\": \"properties(order.api.endpoint)\""), spec);
    assertTrue(Files.exists(projectRoot.resolve("src/test/resources/files/order/request/create-order-invalid.json")));

    Properties config = load("src/test/resources/configuration.properties");
    assertEquals("${ORDER_API_HOST:http://localhost:8080}", config.getProperty("api.service.order-api.host"));
    assertEquals("order-api", config.getProperty("api.service.order-api.default_specification"));
    Properties values = load("src/test/resources/application.properties");
    assertEquals("/order", values.getProperty("order.api.endpoint"));
    assertEquals("sample-value", values.getProperty("test.order.field"));
  }

  @Test
  void apiPlanForIdLookupUsesPathParamAndExpectsNotFoundOnFailure() throws IOException {
    new TestPlanSkill().execute(
        new TestPlanSkill.Input("get order by id", "api", "order", List.of()), writeContext());

    String content = Files.readString(projectRoot.resolve("src/test/resources/features/api/order/get-order-by-id.feature"));
    assertAllStepsLink(content);
    assertTrue(content.contains("Given [api] prepare pathParam for id with value \"properties(test.order.id)\""), content);
    assertTrue(content.contains("Given [api] prepare pathParam for id with value \"properties(test.order.invalid-id)\""), content);
    assertTrue(content.contains("Then [api] response statusCode should be 404"), content);
    String spec = Files.readString(projectRoot.resolve("src/test/resources/files/order/request/get-order.json"));
    assertTrue(spec.contains("\"httpMethod\": \"GET\""), spec);
    assertFalse(spec.contains("payload"), spec);
    Properties values = load("src/test/resources/application.properties");
    assertEquals("/order/{id}", values.getProperty("order.api.endpoint"));
    assertTrue(values.containsKey("test.order.id"));
    assertTrue(values.containsKey("test.order.invalid-id"));
  }

  @Test
  void rerunningAWrittenPlanKeepsFilesAndPropertiesIdempotent() throws IOException {
    TestPlanSkill skill = new TestPlanSkill();
    TestPlanSkill.Input input = new TestPlanSkill.Input("create order returns success", "api", "order", List.of());
    skill.execute(input, writeContext());
    String config = Files.readString(projectRoot.resolve("src/test/resources/configuration.properties"));
    String values = Files.readString(projectRoot.resolve("src/test/resources/application.properties"));

    String second = skill.execute(input, writeContext());

    assertTrue(second.contains("exists src/test/resources/features/api/order/create-order-returns-success.feature"), second);
    assertEquals(config, Files.readString(projectRoot.resolve("src/test/resources/configuration.properties")));
    assertEquals(values, Files.readString(projectRoot.resolve("src/test/resources/application.properties")));
  }

  @Test
  void uiPlanLinksEveryStepAndWritesPageUrlsAndTestData() throws IOException {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("login to saucedemo and land on inventory", "ui", "saucedemo", List.of()),
        writeContext());

    Path feature = projectRoot.resolve("src/test/resources/features/ui/saucedemo/login-to-saucedemo-and-land-on-inventory.feature");
    assertTrue(Files.exists(feature), output);
    assertAllStepsLink(Files.readString(feature));
    Properties values = load("src/test/resources/application.properties");
    assertTrue(values.containsKey("web.page.desktop.login.url"), values.toString());
    assertTrue(values.containsKey("web.page.desktop.inventory.url"), values.toString());
    assertTrue(values.containsKey("test.user.username"), values.toString());
    assertTrue(values.containsKey("test.invalid-user.password"), values.toString());
  }

  @Test
  void kafkaPlanLinksEveryStepAndUsesTheConfiguredTopicAlias() {
    String feature = featureOf(new TestPlanSkill().execute(
        new TestPlanSkill.Input("publish order event to topic", "kafka", "order", List.of()), context()));

    assertAllStepsLink(feature);
    assertTrue(feature.contains("Given [kafka] start kafka producer for orderKafka"), feature);
    assertTrue(feature.contains("When [kafka] send kafka message to topic \"orderEvent\" with key "
        + "\"properties(test.order.id)\" and data \"properties(test.order.payload)\""), feature);
  }

  @Test
  void databaseAndElasticPlansRenderBuiltInStepsWithTypedValues() {
    String sql = featureOf(new TestPlanSkill().execute(
        new TestPlanSkill.Input("query order table by id", "sql", "order", List.of()), context()));
    String mongo = featureOf(new TestPlanSkill().execute(
        new TestPlanSkill.Input("query order collection by id", "mongo", "order", List.of()), context()));
    String elastic = featureOf(new TestPlanSkill().execute(
        new TestPlanSkill.Input("search order index by id", "elastic", "order", List.of()), context()));

    for (String feature : List.of(sql, mongo, elastic)) assertFalse(feature.contains("# MISSING"), feature);
    assertTrue(sql.contains("Given [sql] connect to database with name orderDb"), sql);
    assertTrue(sql.contains("Then [sql] assign previous database response to orderRows"), sql);
    assertTrue(mongo.contains("Given [mongo] select collection with name order"), mongo);
    assertTrue(elastic.contains("Given [elastic-search] connect to elastic search with name order"), elastic);
    assertTrue(elastic.contains("When [elastic-search] assign data orderResults from index order with query :\n"
        + "      | key         | value |"), elastic);
  }

  @Test
  void mongoPlanQueryTableHasKeyValueHeader() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("query order collection by id", "mongo", "order", List.of()), context());

    assertTrue(output.contains("When [mongo] select data with query :\n      | key   | value |"), output);
  }

  @Test
  void createFilesWritesInSingleModeWithoutWriteOption() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("create order returns success", "api", "order", List.of(),
            null, null, true, false),
        new AgentContext(projectRoot, profile(), AgentMode.APPLY, null, Map.of("format", "concise")));

    assertTrue(output.startsWith("written: "), output);
    assertTrue(Files.exists(projectRoot.resolve("src/test/resources/features/api/order/create-order-returns-success.feature")));
  }

  @Test
  void planWithUnlinkedStepsIsNotWritten() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("process order over grpc", "grpc", "order", List.of()), writeContext());

    assertTrue(output.contains("write blocked"), output);
    assertFalse(Files.exists(projectRoot.resolve("src/test/resources/features/order/process-order-over-grpc.feature")));
    // MISSING markers are comment lines, never part of a step's text
    assertFalse(output.lines().anyMatch(line -> line.strip().matches("^(Given|When|Then).*# MISSING.*")), output);
  }

  @Test
  void punctuatedIntentWithoutKnownDomainDoesNotFail() {
    assertDoesNotThrow(() -> new TestPlanSkill().execute(
        new TestPlanSkill.Input("Ship it!!", "api", null, List.of()), context()));
  }

  @Test
  void vaguePlanAsksForClarifyingInput() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("test the app", "ui", null, List.of()),
        context());

    assertTrue(output.contains("needs_input: test_plan_ui_context"));
    assertTrue(output.contains("ask_user:"));
    assertTrue(output.contains("page"));
    assertTrue(output.contains("expected outcome"));
    assertTrue(output.contains("hint: call testara_plan again"));
    assertFalse(output.contains("Feature:"));
  }

  @Test
  void completelyBlankPlanAsksForSliceAndDomain() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("test", null, null, List.of()),
        context());

    assertTrue(output.contains("needs_input: test_plan_clarity"));
    assertTrue(output.contains("ask_user:"));
    assertTrue(output.contains("slice"));
    assertTrue(output.contains("domain"));
    assertFalse(output.contains("Feature:"));
  }

  @Test
  void apiPlanWithNoContextAsksForServiceAndEndpoint() {
    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("verify the backend flow works correctly", "api", null, List.of()),
        context());

    assertTrue(output.contains("needs_input: test_plan_api_context"));
    assertTrue(output.contains("service alias"));
    assertTrue(output.contains("HTTP method"));
    assertFalse(output.contains("Feature:"));
  }

  @Test
  void batchPlanCreatesMultipleFeatureFilesUsingExistingActions() throws IOException {
    Path action = projectRoot.resolve("src/main/java/io/github/ygrip/automation/action/LoginActions.java");
    Files.createDirectories(action.getParent());
    Files.writeString(action, """
        package io.github.ygrip.automation.action;

        import io.github.ygrip.testara.ui.model.Action;

        public class LoginActions {
          @Action("login with valid credentials")
          public void loginWithValidCredentials() {}
        }
        """);
    String featureFiles = """
        [
          {
            "path":"src/test/resources/features/ui/login.feature",
            "featureName":"Login",
            "tags":["@ui","@regression","@login"],
            "scenarios":[
              {"name":"Login succeeds","intent":"login with valid credentials and see inventory","tags":["@positive"]}
            ]
          },
          {
            "path":"src/test/resources/features/ui/cart.feature",
            "featureName":"Cart",
            "tags":["@ui","@regression","@cart"],
            "scenarios":[
              {"name":"Cart placeholder","steps":["When user open \\"cart\\" page","Then user is in \\"cart\\" page"],"tags":["@positive"]}
            ]
          },
          {
            "path":"src/test/resources/features/ui/inventory.feature",
            "featureName":"Inventory",
            "tags":["@ui","@regression","@inventory"],
            "scenarios":[
              {"name":"Inventory placeholder","steps":["When user open \\"inventory\\" page","Then user is in \\"inventory\\" page"],"tags":["@positive"]}
            ]
          },
          {
            "path":"src/test/resources/features/ui/broken.feature",
            "featureName":"Broken",
            "tags":["@ui"],
            "scenarios":[
              {"name":"Invented step","steps":["When the robot dances"],"tags":["@positive"]}
            ]
          }
        ]
        """;

    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("batch ui flows", "ui", "saucedemo", List.of(),
            "batch", featureFiles, true, true),
        writeContext());

    assertTrue(output.contains("mode: batch"));
    assertTrue(output.contains("createdFeatureFiles:"));
    assertTrue(output.contains("src/test/resources/features/ui/login.feature"));
    assertTrue(output.contains("src/test/resources/features/ui/inventory.feature"));
    assertTrue(output.contains("usedActions:"));
    assertTrue(output.contains("login with valid credentials"));
    assertTrue(output.contains("tagIndex:"));
    assertTrue(output.contains("@regression"));
    assertTrue(output.contains("filesChanged:"));
    assertTrue(output.contains("created src/test/resources/features/ui/login.feature [feature:Login; scenarios:Login succeeds; tags:@ui @regression @login]"));
    assertTrue(output.contains("created src/test/resources/features/ui/inventory.feature [feature:Inventory; scenarios:Inventory placeholder; tags:@ui @regression @inventory]"));
    assertTrue(output.contains("- src/test/resources/features/ui/broken.feature (1 unlinked"), output);
    assertFalse(Files.exists(projectRoot.resolve("src/test/resources/features/ui/broken.feature")));
    String login = Files.readString(projectRoot.resolve("src/test/resources/features/ui/login.feature"));
    assertTrue(login.contains("When user do \"login with valid credentials\" in \"login\" page with parameter"));
    assertTrue(login.contains("Then user is in \"inventory\" page"));
  }

  @Test
  void batchPlanForApiSliceHasNoBrowserBackground() {
    String featureFiles = """
        [{"path":"src/test/resources/features/api/ping.feature","featureName":"Ping","tags":["@api"],
          "scenarios":[{"name":"Ping","steps":["Given [api] using service with alias ping-api",
            "When [api] process request to \\"files/ping/request/ping\\"",
            "Then [api] response statusCode should be 200"]}]}]
        """;

    String output = new TestPlanSkill().execute(
        new TestPlanSkill.Input("ping", "api", "ping", List.of(), "batch", featureFiles, false, false), context());

    assertFalse(output.contains("user using chrome in desktop"), output);
    assertTrue(output.contains("blockedFeatureFiles:\n- none"), output);
  }

  private String featureOf(String conciseOutput) {
    return conciseOutput.substring(0, conciseOutput.indexOf("\nflavor-score:"));
  }

  private void assertAllStepsLink(String feature) {
    var links = StepLinker.linkFeature(feature, catalog(), List.of());
    assertFalse(links.isEmpty(), feature);
    assertTrue(links.stream().allMatch(StepLinker.Link::matched),
        () -> links.stream().filter(link -> !link.matched()).map(StepLinker.Link::stepLine).toList() + "\n" + feature);
    assertFalse(feature.contains("# MISSING"), feature);
  }

  private Properties load(String relative) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(Files.readString(projectRoot.resolve(relative))));
    return properties;
  }

  private List<FlavorEntry> catalog() {
    assertTrue(FrameworkKnowledgeStore.instance().isLoaded(), "bundled flavor catalog must be generated by the build");
    return FrameworkKnowledgeStore.instance().flavorCatalog();
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, profile(), AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }

  private AgentContext writeContext() {
    return new AgentContext(projectRoot, profile(), AgentMode.APPLY, null,
        Map.of("format", "concise", "write", "true"));
  }

  /** Realistic profile: the indexed Testara built-in catalog, as a project depending on Testara sees it. */
  private TestaraProjectProfile profile() {
    return new TestaraProjectProfile(projectRoot, BuildTool.MAVEN, "21", List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), catalog(), List.of());
  }
}
