package io.github.ygrip.testara.reporter.parser;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class CommentsDeserializer extends CucumberJsonDeserializer<List<String>> {
  protected List<String> deserialize(JsonNode rootNode) {
    List<String> comments = new ArrayList<>();
    rootNode.forEach(commentNode -> {
      if (commentNode.isTextual()) {
        comments.add(commentNode.asText());
      }
      if (commentNode.isObject() && commentNode.has("value")) {
        comments.add(commentNode.get("value").asText());
      }
    });

    return comments;
  }
}
