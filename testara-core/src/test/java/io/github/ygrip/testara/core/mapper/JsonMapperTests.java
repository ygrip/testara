package io.github.ygrip.testara.core.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Tag("mapper")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class JsonMapperTests extends BaseTests {

  @Test
  void objectToObject() throws Exception {
    List<String> input = List.of("1", "2", "3");
    List<Integer> expected = List.of(1, 2, 3);
    List<Integer> processed = MapperHelper.toObject(input, new TypeReference<>() {
    });
    assertThat(processed, equalTo(expected));
  }

  @Test
  void jsonStringToObject() throws Exception {
    String input = """
        {
          "name": "Yunaz Gilang",
          "id": 1,
          "version": 1.0,
          "attributes": [
            "Quality Assurance",
            "Tech"
          ]
        }
        """;
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("name", "Yunaz Gilang");
    expected.put("id", 1);
    expected.put("version", 1.0);
    expected.put("attributes", List.of("Quality Assurance", "Tech"));
    Map<String, Object> processed = MapperHelper.toObject(input, new TypeReference<>() {
    });
    assertThat(processed, equalTo(expected));
  }

  @Test
  void objectToJsonString() throws Exception {
    String expected =
        "{\"name\":\"Yunaz Gilang\",\"id\":1,\"version\":1.0,\"attributes\":[\"Quality Assurance\",\"Tech\"]}";
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("name", "Yunaz Gilang");
    input.put("id", 1);
    input.put("version", 1.0);
    input.put("attributes", List.of("Quality Assurance", "Tech"));
    String processed = MapperHelper.toString(input);
    assertThat(processed, equalTo(expected));
  }
}
