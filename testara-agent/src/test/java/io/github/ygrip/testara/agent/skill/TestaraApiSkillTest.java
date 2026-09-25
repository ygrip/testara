package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraApiSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void explainDocumentsRealRequestSpecModelAndBuiltInFlow() {
    String guide = new TestaraApiSkill().execute(
        new TestaraApiSkill.Input("explain", null, null, null, null),
        context(Map.of()));

    assertTrue(guide.contains("CreateRequestSpecification"));
    assertTrue(guide.contains("queryParameters"));
    assertTrue(guide.contains("multiPartData"));
    assertTrue(guide.contains("When [api] process request to \"files/{domain}/request/{flow}\""));
    assertTrue(guide.contains("response($['{domain}Response'])"));
  }

  @Test
  void requestSpecUsesFilesPathAndOmitsPayloadForGet() {
    String output = new TestaraApiSkill().execute(
        new TestaraApiSkill.Input("request-spec", "checkout", "get-order", "GET", "/orders/{id}"),
        context(Map.of()));

    assertTrue(output.contains("src/test/resources/files/checkout/request/get-order.json"));
    assertTrue(output.contains("When [api] process request to \"files/checkout/request/get-order\""));
    assertTrue(output.contains("\"queryParameters\""));
    assertFalse(output.contains("\"payload\""));
  }

  @Test
  void configSeparatesRuntimeAndEnvironmentValues() {
    String output = new TestaraApiSkill().execute(
        new TestaraApiSkill.Input("config", "checkout", null, null, null),
        context(Map.of()));

    assertTrue(output.contains("api.service.checkout-api.host=${CHECKOUT_API_HOST:http://localhost:8080}"));
    assertFalse(output.contains("api.service.checkout-api.host=properties(checkout.api.host)"));
    assertFalse(output.contains("checkout.api.host=http://localhost:8080"));
    assertTrue(output.contains("test.checkout.field=sample-value"));
  }

  @Test
  void guideOnlyListsRealApiSteps() {
    String guide = new TestaraApiSkill().execute(
        new TestaraApiSkill.Input("explain", null, null, null, null), context(Map.of()));

    assertFalse(guide.contains("prepare header with value"), guide);
    assertFalse(guide.contains("prepare query parameter with value"), guide);
    assertFalse(guide.contains("prepare form param with value"), guide);
    assertTrue(guide.contains("[api] prepare headers with data"), guide);
    assertTrue(guide.contains("[api] prepare queryParams with data"), guide);
    assertTrue(guide.contains("[api] prepare formParams with data"), guide);
  }

  @Test
  void writingConfigTwiceDoesNotDuplicateKeys() throws IOException {
    AgentContext write = new AgentContext(projectRoot, null, AgentMode.APPLY, null,
        Map.of("format", "concise", "write", "true"));
    TestaraApiSkill skill = new TestaraApiSkill();

    skill.execute(new TestaraApiSkill.Input("config", "checkout", null, null, null), write);
    String second = skill.execute(new TestaraApiSkill.Input("config", "checkout", null, null, null), write);

    String config = Files.readString(projectRoot.resolve("src/test/resources/configuration.properties"));
    assertEquals(1, config.lines().filter(line -> line.startsWith("api.service.checkout-api.host=")).count(), config);
    assertTrue(second.contains("unchanged src/test/resources/configuration.properties"), second);
    String values = Files.readString(projectRoot.resolve("src/test/resources/application.properties"));
    assertTrue(values.contains("checkout.api.endpoint=/checkout/{id}"), values);
  }

  private AgentContext context(Map<String, String> options) {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, options);
  }
}
