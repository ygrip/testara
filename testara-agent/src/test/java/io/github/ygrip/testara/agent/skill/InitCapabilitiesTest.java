package io.github.ygrip.testara.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitCapabilitiesTest {

  @Test
  void resolvesCombinedBaseAndDeduplicatesCapabilities() {
    List<String> capabilities = InitCapabilities.normalize("api", List.of("ui", "sql", "mongo", "ui"));

    assertEquals(List.of("ui", "sql", "mongo"), capabilities);
    assertEquals("ui", InitCapabilities.baseType(capabilities));
  }

  @Test
  void preservesLegacyFullstackAndMapsAliases() {
    assertEquals(List.of("api", "ui"), InitCapabilities.normalize("fullstack", List.of()));
    assertEquals(List.of("kafka"), InitCapabilities.normalize("streaming", List.of()));
  }
}
