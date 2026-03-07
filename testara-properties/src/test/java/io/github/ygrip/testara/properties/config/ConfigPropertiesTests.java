package io.github.ygrip.testara.properties.config;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.config.PropertyResolver;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.properties.PropertiesContainerExtension;
import io.github.ygrip.testara.properties.support.ConsulHelper;
import io.github.ygrip.testara.properties.support.VaultHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@ExtendWith(PropertiesContainerExtension.class)
@Tag("properties")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ConfigPropertiesTests extends BaseTests {

  @Test
  void getValueLoadedFromConsul() {
    Optional<String> value = TestFramework.context().configuration().get("api.service.quest.host");
    if (TestFramework.context().get(ConsulHelper.class).isEnabled()) {
      assertThat(value.isPresent(), equalTo(true));
      assertThat(value.get(), not(blankOrNullString()));
    } else {
      assertThat(value.isPresent(), equalTo(false));
    }
  }

  @Test
  void getValueLoadedFromVault() {
    Optional<String> value = TestFramework.context().configuration().get("environment");
    if (TestFramework.context().get(VaultHelper.class).isEnabled()) {
      assertThat(value.isPresent(), equalTo(true));
      assertThat(value.get(), not(blankOrNullString()));
    } else {
      assertThat(value.isPresent(), equalTo(false));
    }
  }

  @Test
  void getValuesLoadedFromConsul() {
    Map<String, PropertyResolver.PropertyValue> value =
        TestFramework.context().configuration().getByPrefix("api.service.quest");
    if (TestFramework.context().get(ConsulHelper.class).isEnabled()) {
      assertThat(value, not(anEmptyMap()));
    } else {
      assertThat(value, anEmptyMap());
    }
  }
}
