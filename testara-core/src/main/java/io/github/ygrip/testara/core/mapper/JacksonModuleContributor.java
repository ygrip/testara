package io.github.ygrip.testara.core.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public interface JacksonModuleContributor {
  void contribute(ObjectMapper mapper);

  void contribute(XmlMapper mapper);
}
