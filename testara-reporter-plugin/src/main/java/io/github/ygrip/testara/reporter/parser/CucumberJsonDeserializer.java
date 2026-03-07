package io.github.ygrip.testara.reporter.parser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public abstract class CucumberJsonDeserializer<T> extends JsonDeserializer<T> {

  public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    JsonNode rootNode = parser.getCodec().readTree(parser);
    return this.deserialize(rootNode);
  }

  protected abstract T deserialize(JsonNode node) throws IOException;
}
