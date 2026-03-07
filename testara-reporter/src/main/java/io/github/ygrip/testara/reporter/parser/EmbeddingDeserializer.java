package io.github.ygrip.testara.reporter.parser;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ygrip.testara.reporter.cucumber.Embedding;

public class EmbeddingDeserializer extends CucumberJsonDeserializer<List<Embedding>> {
  protected List<Embedding> deserialize(JsonNode rootNode) {
    List<Embedding> embeddings = new ArrayList<>();
    if (rootNode != null) {
      if (rootNode.isArray()) {
        rootNode.forEach(node -> {
          embeddings.add(this.getEmbedding(node));
        });
      } else {
        embeddings.add(this.getEmbedding(rootNode));
      }
    }

    return embeddings;
  }

  private Embedding getEmbedding(JsonNode node) {
    String data = "";
    String mimeType = "";
    String name = "";
    if(node.has("data")){
      data = node.get("data").asText();
    }
    if(node.has("mime_type")){
      mimeType = node.get("mime_type").asText();
    }
    if(node.has("name")){
      name = node.get("name").asText();
    }

    return name.isEmpty() ? new Embedding(mimeType, data) : new Embedding(mimeType, data, name);
  }
}
