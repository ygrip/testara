package io.github.ygrip.testara.command.properties;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>CommandExecutorProperties class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Data
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "command.executor")
public class CommandExecutorProperties {
  private Integer maxPrintableCharacters = 100000;
  private Integer maxExecutionCacheSize = 500;
  private Integer maxParseCacheSize = 1000;
  private String defaultCommandSeparator = ",";
  private Boolean cacheEnabled = true;
  private Integer scanTimeout = 10;
  private Set<String> scanLocations = new HashSet<>(Collections.singletonList("io.github.ygrip.testara"));
}
