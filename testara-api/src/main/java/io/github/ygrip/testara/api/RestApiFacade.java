package io.github.ygrip.testara.api;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.restassured.config.JsonConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.ParamConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.path.json.config.JsonPathConfig;

public interface RestApiFacade {
  String serviceName();

  default RestAssuredConfig config() {
    return RestAssuredConfig.config()
      // Ensures params are not duplicated across retries/spec reuse
      .paramConfig(ParamConfig.paramConfig()
        .replaceAllParameters())

      // Jackson ObjectMapper for BOTH request & response
      .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
        .defaultObjectMapperType(ObjectMapperType.JACKSON_2)
        .jackson2ObjectMapperFactory((cls, charset) -> mapper()))

      // JsonPath handling (response side)
      .jsonConfig(JsonConfig.jsonConfig()
        .numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));
  }

  default ObjectMapper mapper() {
    return JsonMapper.builder()
      // Allow string values where objects/numbers are expected
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)

      // Relaxed JSON parsing
      .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)

      // Prevent hard failures
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

      // Java time support
      .addModule(new JavaTimeModule())

      // Stable number handling
      .build();
  }
}
