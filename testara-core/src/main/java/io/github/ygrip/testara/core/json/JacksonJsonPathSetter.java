package io.github.ygrip.testara.core.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JacksonJsonPathSetter {

  private static final Pattern TOKEN =
    Pattern.compile("(\\w+)|(\\[(\\d+)])");

  private JacksonJsonPathSetter() {}

  public static void set(ObjectNode root, String jsonPath, JsonNode value) {

    if (!jsonPath.startsWith("$.")) {
      throw new IllegalArgumentException("Only supports paths starting with $. ");
    }

    String path = jsonPath.substring(2);
    Matcher matcher = TOKEN.matcher(path);

    JsonNode current = root;
    JsonNode parent = null;
    String lastField = null;
    Integer lastIndex = null;

    while (matcher.find()) {

      parent = current;

      String field = matcher.group(1);
      String indexStr = matcher.group(3);

      if (field != null) {

        lastField = field;
        lastIndex = null;

        if (!current.has(field) || current.get(field).isNull()) {
          ((ObjectNode) current).set(field, JsonNodeFactory.instance.objectNode());
        }

        current = current.get(field);

      } else if (indexStr != null) {

        int index = Integer.parseInt(indexStr);
        lastIndex = index;
        lastField = null;

        if (!current.isArray()) {
          ArrayNode newArray = JsonNodeFactory.instance.arrayNode();
          ((ObjectNode) parent).set(lastField, newArray);
          current = newArray;
        }

        ArrayNode array = (ArrayNode) current;

        while (array.size() <= index) {
          array.addNull();
        }

        if (array.get(index).isNull()) {
          array.set(index, JsonNodeFactory.instance.objectNode());
        }

        current = array.get(index);
      }
    }

    // Final assignment
    if (lastField != null) {
      ((ObjectNode) parent).set(lastField, value);
    } else if (lastIndex != null) {
      ((ArrayNode) parent).set(lastIndex, value);
    }
  }
}
