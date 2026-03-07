package io.github.ygrip.testara.core.config;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@Tag("config")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ConfigTests extends BaseTests {

  @Test
  void getValidProperties() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("class.loader.default-scan-locations");
    assertThat(properties.isPresent(), equalTo(true));
  }

  @Test
  void resolvePropertyPlaceholder() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("property.name");
    assertThat(properties.isPresent(), equalTo(true));
    String value = properties.get();
    assertThat(value, not(equalTo("-of-")));
  }

  @Test
  void resolvePropertyPlaceholderWithFallback() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("property.fallback");
    assertThat(properties.isPresent(), equalTo(true));
    String value = properties.get();
    assertThat(value, equalTo("default-of-default"));
  }

  @Test
  void getInvalidProperties() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("class.loader.random-data");
    assertThat(properties.isPresent(), equalTo(false));
  }

  @Test
  void getPropertiesWithFallback() throws Exception {
    String expected = "data";
    String properties = TestFramework.context().configuration().get("class.loader.random-data", expected);
    assertThat(properties, equalTo(expected));
  }

  @Test
  void getMappedObjectFromProperties() throws Exception {
    String expected = "data";
    String properties = TestFramework.context().configuration().get("class.loader.random-data", expected);
    assertThat(properties, equalTo(expected));
  }
}
