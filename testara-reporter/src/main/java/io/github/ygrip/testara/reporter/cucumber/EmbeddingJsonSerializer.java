package io.github.ygrip.testara.reporter.cucumber;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/** Keeps externalized screenshot markers lightweight when reports are merged. */
public final class EmbeddingJsonSerializer extends StdSerializer<Embedding> {
  public EmbeddingJsonSerializer() {
    super(Embedding.class);
  }

  @Override
  public void serialize(Embedding value, JsonGenerator generator, SerializerProvider provider) throws IOException {
    generator.writeStartObject();
    writeString(generator, "mime_type", value.getStoredMimeType());
    writeString(generator, "data", value.getStoredData());
    writeString(generator, "name", value.getName());
    writeString(generator, "fileId", value.getFileId());
    generator.writeEndObject();
  }

  private static void writeString(JsonGenerator generator, String field, String value) throws IOException {
    if (value != null) {
      generator.writeStringField(field, value);
    }
  }
}
