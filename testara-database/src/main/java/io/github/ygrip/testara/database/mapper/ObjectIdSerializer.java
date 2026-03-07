package io.github.ygrip.testara.database.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.bson.types.ObjectId;

import java.io.IOException;

/**
 * <p>ObjectIdSerializer class.</p>
 *
 * @author yunaz.ramadhan on 1/21/2020
 * @version $Id: $Id
 */
public final class ObjectIdSerializer extends JsonSerializer<ObjectId> {
  /** {@inheritDoc} */
  @Override
  public void serialize(ObjectId objectId,
      JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeString(objectId.toString());
  }
}
