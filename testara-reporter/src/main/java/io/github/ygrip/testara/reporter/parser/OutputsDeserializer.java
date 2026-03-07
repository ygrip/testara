package io.github.ygrip.testara.reporter.parser;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ygrip.testara.reporter.cucumber.Output;

public class OutputsDeserializer extends CucumberJsonDeserializer<List<Output>> {
  protected List<Output> deserialize(JsonNode rootNode) {
    List<Output> outputs = new ArrayList<>();
    if (rootNode.get(0) != null) {
      if (rootNode.get(0).isArray()) {
        rootNode.forEach(node -> {
          outputs.add(this.getOutput(node));
        });
      } else {
        outputs.add(this.getOutput(rootNode));
      }
    }

    return outputs;
  }

  private Output getOutput(JsonNode outputNode) {
    List<String> messages = new ArrayList<>();

    outputNode.forEach(node -> {
      messages.add(node.asText());
    });

    return new Output(messages);
  }
}
