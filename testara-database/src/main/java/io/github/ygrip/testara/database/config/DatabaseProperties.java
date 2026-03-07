package io.github.ygrip.testara.database.config;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.database.model.SqlModel;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>DatabaseProperties class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "sql")
public class DatabaseProperties {
  private Map<String, SqlModel> service = new HashMap<>();
}
