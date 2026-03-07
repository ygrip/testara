package io.github.ygrip.testara.properties.vault;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.properties.PropertiesContainerExtension;
import io.github.ygrip.testara.properties.support.VaultHelper;
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
public class VaultPropertiesTests extends BaseTests {

  @Test
  void getValuesFromVault() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    Map<String, String> values = vault.getValues();
    if (vault.isEnabled()) {
      assertThat(values, not(anEmptyMap()));
    } else {
      assertThat(values, anEmptyMap());
    }
  }

  @Test
  void getValuesFromVaultWithCustomPath() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    Map<String, String> values = vault.getValues("config/testara-automation/qa");
    if (vault.isEnabled()) {
      assertThat(values, not(anEmptyMap()));
    } else {
      assertThat(values, anEmptyMap());
    }
  }

  @Test
  void getValuesFromVaultWithNonExistingPath() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    Map<String, String> values = vault.getValues("config/random-path");
    assertThat(values, anEmptyMap());
  }

  @Test
  void getValueFromVault() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    String value = vault.getValue("type");
    if (vault.isEnabled()) {
      assertThat(value, not(blankOrNullString()));
    } else {
      assertThat(value, blankOrNullString());
    }
  }

  @Test
  void getValueFromVaultWithCustomPath() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    String value = vault.getValue("config/testara-automation/qa", "environment");
    if (vault.isEnabled()) {
      assertThat(value, not(blankOrNullString()));
    } else {
      assertThat(value, blankOrNullString());
    }
  }

  @Test
  void getValueFromVaultWithNonExistingPath() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    String value = vault.getValue("config/random-path", "type");
    assertThat(value, blankOrNullString());
  }

  @Test
  void getValueFromVaultWithNonExistingKey() {
    VaultHelper vault = TestFramework.context().get(VaultHelper.class);
    String value = vault.getValue("random-key");
    assertThat(value, blankOrNullString());
  }
}
