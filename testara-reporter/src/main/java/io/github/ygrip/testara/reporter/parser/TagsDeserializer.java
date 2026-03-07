package io.github.ygrip.testara.reporter.parser;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ygrip.testara.reporter.cucumber.Location;
import io.github.ygrip.testara.reporter.cucumber.Tag;

public class TagsDeserializer extends CucumberJsonDeserializer<List<Tag>> {
  protected List<Tag> deserialize(JsonNode rootNode) {
    List<Tag> tags = new ArrayList<>();
    rootNode.forEach(node -> {
      String tagName = node.get("name").asText();
      String type = null;
      if (node.has("type")) {
        type = node.get("type").asText();
      }
      Location location = null;
      if (node.has("location")) {
        JsonNode locationNode = node.get("location");
        if (locationNode != null) {
          int line = locationNode.get("line").asInt();
          int column = locationNode.get("column").asInt();
          location = new Location(line, column);
        }
      }
      Tag tag = new Tag(tagName);
      if (type != null) {
        tag.setType(type);
      }
      if (location != null) {
        tag.setLocation(location);
      }
      tags.add(tag);
    });

    return tags;
  }
}
