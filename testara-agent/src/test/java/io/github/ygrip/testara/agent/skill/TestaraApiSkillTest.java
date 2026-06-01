package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

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

  private AgentContext context(Map<String, String> options) {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, options);
  }
}
