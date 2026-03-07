package io.github.ygrip.testara.database.config;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.database.model.MongoModel;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>MongoProperties class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "mongo")
public class MongoProperties {
  private Map<String, MongoModel> service = new HashMap<>();
  private boolean preEmptiveConnectionEnabled = false;
}
