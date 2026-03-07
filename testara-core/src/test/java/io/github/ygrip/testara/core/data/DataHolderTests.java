package io.github.ygrip.testara.core.data;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultData;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Log4j2
@Tag("data")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class DataHolderTests extends BaseTests {

  @Test
  void getRequestDataFromDefinedPath() throws Exception {
    String name = "Yunaz Gilang";
    TestFramework.context().get(DummyRequestDataHolder.class).setName(name);
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    Map.Entry<DefaultData, Object> stored = dataHolder.getRequest("$.name");
    assertThat(stored, notNullValue());
    assertThat(stored.getKey().getClass(), equalTo(DummyRequestDataHolder.class));
    assertThat(stored.getValue(), equalTo(name));
  }

  @Test
  void getRequestDataFromUndefinedPath() throws Exception {
    final String anonymousKey = "$.arguments";
    final List<String> args = List.of("Quality Assurance");
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    dataHolder.setRequest(anonymousKey, args);
    Map.Entry<DefaultData, Object> stored = dataHolder.getRequest(anonymousKey);
    assertThat(stored, notNullValue());
    assertThat(stored.getValue(), equalTo(args));
  }

  @Test
  void getResponseDataFromDefinedPath() throws Exception {
    String name = "Yunaz Gilang";
    TestFramework.context().get(DummyResponseDataHolder.class).setName(name);
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    Map.Entry<DefaultData, Object> stored = dataHolder.getResponse("$.name");
    assertThat(stored, notNullValue());
    assertThat(stored.getKey().getClass(), equalTo(DummyResponseDataHolder.class));
    assertThat(stored.getValue(), equalTo(name));
  }

  @Test
  void getResponseDataFromUndefinedPath() throws Exception {
    final String anonymousKey = "$.arguments";
    final List<String> args = List.of("Quality Assurance");
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    dataHolder.setResponse(anonymousKey, args);
    Map.Entry<DefaultData, Object> stored = dataHolder.getResponse(anonymousKey);
    assertThat(stored, notNullValue());
    assertThat(stored.getValue(), equalTo(args));
  }

  @Test
  void getRequestDataFromDefinedField() throws Exception {
    String name = "Yunaz Gilang";
    TestFramework.context().get(DummyRequestDataHolder.class).setName(name);
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    Map.Entry<DefaultData, Object> stored = dataHolder.getRequest("name");
    assertThat(stored, notNullValue());
    assertThat(stored.getKey().getClass(), equalTo(DummyRequestDataHolder.class));
    assertThat(stored.getValue(), equalTo(name));
  }

  @Test
  void getRequestDataFromUndefinedField() throws Exception {
    final String anonymousKey = "arguments";
    final List<String> args = List.of("Quality Assurance");
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    dataHolder.setRequest(anonymousKey, args);
    Map.Entry<DefaultData, Object> stored = dataHolder.getRequest(anonymousKey);
    assertThat(stored, notNullValue());
    assertThat(stored.getValue(), equalTo(args));
  }

  @Test
  void getResponseDataFromDefinedField() throws Exception {
    String name = "Yunaz Gilang";
    TestFramework.context().get(DummyResponseDataHolder.class).setName(name);
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    Map.Entry<DefaultData, Object> stored = dataHolder.getResponse("name");
    assertThat(stored, notNullValue());
    assertThat(stored.getKey().getClass(), equalTo(DummyResponseDataHolder.class));
    assertThat(stored.getValue(), equalTo(name));
  }

  @Test
  void getResponseDataFromUndefinedField() throws Exception {
    final String anonymousKey = "$.arguments";
    final List<String> args = List.of("Quality Assurance");
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    dataHolder.setResponse(anonymousKey, args);
    Map.Entry<DefaultData, Object> stored = dataHolder.getResponse(anonymousKey);
    assertThat(stored, notNullValue());
    assertThat(stored.getValue(), equalTo(args));
  }

  @Test
  void getComplexDataWithHumanReadablePath() throws Exception {
    final String anonymousKey = "complex attributes 0 name";
    final String name = "Quality Assurance";
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    dataHolder.setRequest(anonymousKey, name);
    Map.Entry<DefaultData, Object> stored = dataHolder.getRequest(anonymousKey);
    assertThat(stored, notNullValue());
    assertThat(stored.getValue(), equalTo(name));
  }
}
