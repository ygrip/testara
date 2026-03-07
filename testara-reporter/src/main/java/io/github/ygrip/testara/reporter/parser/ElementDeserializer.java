package io.github.ygrip.testara.reporter.parser;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ygrip.testara.reporter.cucumber.Element;

public class ElementDeserializer extends CucumberJsonDeserializer<List<Element>> {
  protected List<Element> deserialize(JsonNode rootNode) {
    List<Element> elements = new ArrayList<>();
    if (rootNode.isArray()) {
      for (int i = 0; i < rootNode.size(); i++) {
        JsonNode node = rootNode.get(i);
        try {
          Element element = CucumberReportParser.getMapper().treeToValue(node, Element.class);
          element.setIndex(i);
          elements.add(element);
        } catch (Exception ignored) {

        }
      }
    }

    return elements;
  }
}
