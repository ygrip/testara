package io.github.ygrip.testara.core.mapper.module;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * <p>NullKeySerializer class.</p>
 *
 * @author yunaz.ramadhan on 6/24/2020
 * @version $Id: $Id
 */
public class NullKeySerializer extends JsonSerializer<Object> {
  /** {@inheritDoc} */
  @Override
  public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeFieldName("");
  }
}
