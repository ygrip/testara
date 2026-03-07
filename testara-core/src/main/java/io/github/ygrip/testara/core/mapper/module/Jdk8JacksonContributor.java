package io.github.ygrip.testara.core.mapper.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.github.ygrip.testara.core.mapper.JacksonModuleContributor;

public class Jdk8JacksonContributor implements JacksonModuleContributor {

  @Override
  public void contribute(ObjectMapper mapper) {
    mapper.registerModule(new Jdk8Module())
        .getSerializerProvider()
        .setNullKeySerializer(new NullKeySerializer());
  }

  @Override
  public void contribute(XmlMapper mapper) {

  }
}
