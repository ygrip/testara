package io.github.ygrip.testara.core.mapper.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.ygrip.testara.core.mapper.JacksonModuleContributor;

public class DuplicateJacksonContributor implements JacksonModuleContributor {

  @Override
  public void contribute(ObjectMapper mapper) {
    SimpleModule mergeDuplicatesModule = new SimpleModule("Merge duplicated fields in array");
    mergeDuplicatesModule.addDeserializer(JsonNode.class, new MergeDuplicateFieldsJsonNodeDeserializer());
    mapper.registerModule(mergeDuplicatesModule);
  }

  @Override
  public void contribute(XmlMapper mapper) {
    SimpleModule mergeDuplicatesModule = new SimpleModule("Merge duplicated fields in array");
    mergeDuplicatesModule.addDeserializer(JsonNode.class, new MergeDuplicateFieldsJsonNodeDeserializer());
    mapper.registerModule(mergeDuplicatesModule);
  }
}
