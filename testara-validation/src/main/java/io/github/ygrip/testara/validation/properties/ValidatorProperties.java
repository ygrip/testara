package io.github.ygrip.testara.validation.properties;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>ValidatorProperties class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Data
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "validator.helper")
public class ValidatorProperties {
  private Set<String> scanLocations = new HashSet<>(Collections.singletonList("io.github.ygrip.testara"));
  private Integer scanTimeout = 10;
  /**
   * Timeout for each validation task in seconds (default: 30)
   */
  private Integer timeoutSeconds = 30;
  private String validationsPath;
}
