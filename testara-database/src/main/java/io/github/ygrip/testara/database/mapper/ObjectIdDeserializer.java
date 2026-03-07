package io.github.ygrip.testara.database.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

import java.io.IOException;

/**
 * <p>ObjectIdDeserializer class.</p>
 *
 * @author yunaz.ramadhan on 1/21/2020
 * @version $Id: $Id
 */
public final class ObjectIdDeserializer extends JsonDeserializer<ObjectId> {

  /** {@inheritDoc} */
  @Override
  public ObjectId deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode oid = ((JsonNode) p.readValueAsTree()).get("$oid");
    return new ObjectId(oid.asText());
  }

}
