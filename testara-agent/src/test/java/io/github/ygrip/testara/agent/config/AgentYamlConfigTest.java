package io.github.ygrip.testara.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
          requestSpecRoots:
            - "src/test/resources/files"
        tagAliases:
          checkout:
            - "@checkout"
            - "@purchase"
          smoke:
            - "@smoke"
        """);

    assertEquals("false", config.run().get("dryRun"));
    assertEquals("true", config.run().get("execute"));
    assertEquals("true", config.write().get("enabled"));
    assertEquals("qwen3", config.llm().get("model"));
    assertEquals(1, config.featureRoots().size());
    assertEquals(1, config.requestSpecRoots().size());
    assertEquals(2, config.tagAliases().get("checkout").size());
    assertEquals(1, config.tagAliases().get("smoke").size());

    var options = new LinkedHashMap<String, String>();
    config.apply(options);
    assertNull(options.get("write"), "write.enabled: true must never enable writes (APPLY) by itself");
    assertEquals("qwen3", options.get("llm.model"));
    assertEquals("(@checkout or @purchase)", options.get("tag-alias.checkout"));
  }

  @Test
  void writeEnabledFalseDisablesWritesEvenWhenAlreadyRequested() {
    AgentYamlConfig.AgentConfig config = AgentYamlConfig.parse("write:\n  enabled: false\n");

    var options = new LinkedHashMap<String, String>();
    options.put("write", "true");
    config.apply(options);

    assertEquals("false", options.get("write"));
  }

  @Test
  void handlesInlineCommentsInlineListsQuotesAndSameIndentLists() {
    AgentYamlConfig.AgentConfig config = AgentYamlConfig.parse("""
        # leading comment
        format: concise   # top-level scalar before any section
        run:
          dryRun: false  # inline comment
          format: 'json'
        project:
          featureRoots: [src/test/resources/features, 'features']
          validationRoots:
          - src/test/resources/validations
        tagAliases:
          smoke: "@smoke"
          checkout: ['@checkout', "@purchase"]
        """);

    assertEquals("concise", config.general().get("format"));
    assertEquals("false", config.run().get("dryRun"));
    assertEquals("json", config.run().get("format"));
    assertEquals(List.of("src/test/resources/features", "features"), config.featureRoots());
    assertEquals(List.of("src/test/resources/validations"), config.validationRoots());
    assertEquals(List.of("@smoke"), config.tagAliases().get("smoke"));
    assertEquals(List.of("@checkout", "@purchase"), config.tagAliases().get("checkout"));
  }

  @Test
  void emptyOrInvalidFileYieldsEmptyConfig(@TempDir Path root) throws IOException {
    assertTrue(AgentYamlConfig.parse("").featureRoots().isEmpty());
    assertTrue(AgentYamlConfig.parse("run: [unclosed").run().isEmpty());
    assertTrue(AgentYamlConfig.parse("- just\n- a list\n").general().isEmpty());

    Files.writeString(root.resolve("testara-agent.yaml"), "run:\n  dryRun: : :\n\tbad");
    assertTrue(AgentYamlConfig.load(root).run().isEmpty());
  }
}
