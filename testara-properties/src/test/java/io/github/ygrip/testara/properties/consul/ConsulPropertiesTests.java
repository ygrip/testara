package io.github.ygrip.testara.properties.consul;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.properties.PropertiesContainerExtension;
import io.github.ygrip.testara.properties.support.ConsulHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;

@ExtendWith(PropertiesContainerExtension.class)
@Tag("properties")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ConsulPropertiesTests extends BaseTests {

  @Test
  void getValuesFromConsul() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    Map<String, String> values = consul.getValues();
    if (consul.isEnabled()) {
      assertThat(values, not(anEmptyMap()));
    } else {
      assertThat(values, anEmptyMap());
    }
  }

  @Test
  void getValuesFromConsulWithCustomPath() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    Map<String, String> values = consul.getValues("config/testara-automation/qa/");
    if (consul.isEnabled()) {
      assertThat(values, not(anEmptyMap()));
    } else {
      assertThat(values, anEmptyMap());
    }
  }

  @Test
  void getValuesFromConsulWithNonExistingPath() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    Map<String, String> values = consul.getValues("config/random-path/");
    assertThat(values, anEmptyMap());
  }

  @Test
  void getValueFromConsul() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    String value = consul.getValue("api.service.quest.host");
    if (consul.isEnabled()) {
      assertThat(value, not(blankOrNullString()));
    } else {
      assertThat(value, blankOrNullString());
    }
  }

  @Test
  void getValueFromConsulWithCustomPath() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    String value = consul.getValue("config/testara-automation/qa/", "api.service.quest.host");
    if (consul.isEnabled()) {
      assertThat(value, not(blankOrNullString()));
    } else {
      assertThat(value, blankOrNullString());
    }
  }

  @Test
  void getValueFromConsulWithNonExistingPath() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    String value = consul.getValue("git/random-path", "api.service.quest.host");
    assertThat(value, blankOrNullString());
  }

  @Test
  void getValueFromConsulWithNonExistingKey() {
    ConsulHelper consul = TestFramework.context().get(ConsulHelper.class);
    String value = consul.getValue("random-key");
    assertThat(value, blankOrNullString());
  }
}
