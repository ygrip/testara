package io.github.ygrip.testara.agent.knowledge;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-all.7: parseSimpleJson was a hand-rolled comma/colon splitter
 * that could mis-split on commas or colons embedded inside quoted string values (e.g. a Windows
 * path fingerprint like "src\\main\\java\\Foo.java", or an error message containing a colon).
 * Now backed by Jackson, which parses JSON correctly regardless of what characters appear inside
 * quoted values.
 */
class JsonlKnowledgeStoreJsonParsingTest {

  @Test
  void parsesValuesContainingCommasAndColons() {
    String line = "{\"path\":\"src/main/java/Foo.java\",\"type\":\"COMMAND\",\"size\":42,"
        + "\"lastModifiedMillis\":1000,\"sha256\":\"abc:def,ghi\"}";

    Map<String, String> parsed = JsonlKnowledgeStore.parseSimpleJson(line);

    assertEquals("src/main/java/Foo.java", parsed.get("path"));
    assertEquals("COMMAND", parsed.get("type"));
    assertEquals("42", parsed.get("size"));
    assertEquals("abc:def,ghi", parsed.get("sha256"), "a colon/comma inside a quoted value must not split the field");
  }

  @Test
  void returnsEmptyMapInsteadOfThrowingOnMalformedJson() {
    Map<String, String> parsed = JsonlKnowledgeStore.parseSimpleJson("{not valid json");

    assertTrue(parsed.isEmpty());
  }
}
