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

  @Test
  void explicitNonDefaultTypeIsKeptAlongsideSlices() {
    assertEquals(List.of("ui", "sql"), InitCapabilities.normalize("ui", List.of("sql")));
    assertEquals("ui", InitCapabilities.contentType("ui", List.of("sql")));
  }

  @Test
  void unknownTypesAreNotSupported() {
    assertEquals(false, InitCapabilities.isSupported(InitCapabilities.contentType("../../x", List.of())));
  }
}
