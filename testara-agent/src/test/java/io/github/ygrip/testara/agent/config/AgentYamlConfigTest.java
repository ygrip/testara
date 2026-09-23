package io.github.ygrip.testara.agent.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentYamlConfigTest {

  @Test
  void parsesNestedScalarsListsAndAliases() {
    AgentYamlConfig.AgentConfig config = AgentYamlConfig.parse("""
        run:
          dryRun: false
          execute: true
        write:
          enabled: true
        llm:
          model: "qwen3"
        project:
          featureRoots:
            - "src/test/resources/features"
        tagAliases:
          checkout:
            - "@checkout"
            - "@purchase"
        """);

    assertEquals("false", config.run().get("dryRun"));
    assertEquals("true", config.run().get("execute"));
    assertEquals("true", config.write().get("enabled"));
    assertEquals("qwen3", config.llm().get("model"));
    assertEquals(1, config.featureRoots().size());
    assertEquals(2, config.tagAliases().get("checkout").size());

    var options = new LinkedHashMap<String, String>();
    config.apply(options);
    assertEquals("true", options.get("write"));
    assertEquals("qwen3", options.get("llm.model"));
    assertEquals("(@checkout or @purchase)", options.get("tag-alias.checkout"));
  }
}
