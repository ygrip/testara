package io.github.ygrip.testara.database.mapper.module;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.ygrip.testara.core.mapper.JacksonModuleContributor;
import io.github.ygrip.testara.database.mapper.ObjectIdDeserializer;
import io.github.ygrip.testara.database.mapper.ObjectIdSerializer;
import org.bson.types.ObjectId;

public class ObjectIdJacksonContributor implements JacksonModuleContributor {

  @Override
  public void contribute(ObjectMapper mapper) {
    SimpleModule mod = new SimpleModule("ObjectId", new Version(1, 0, 0, null, null, null));
    mod.addDeserializer(ObjectId.class, new ObjectIdDeserializer());
    mod.addSerializer(ObjectId.class, new ObjectIdSerializer());
    mapper.registerModule(mod);
  }

  @Override
  public void contribute(XmlMapper mapper) {

  }
}
