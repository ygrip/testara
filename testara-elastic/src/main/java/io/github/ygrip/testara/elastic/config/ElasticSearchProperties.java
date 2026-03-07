package io.github.ygrip.testara.elastic.config;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.elastic.model.ElasticSearchModel;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>ElasticSearchProperties class.</p>
 *
 * @author yunaz.ramadhan on 4/19/2021
 * @version $Id: $Id
 */
@Data
@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "elasticsearch")
public class ElasticSearchProperties {
  private Map<String, ElasticSearchModel> service = new HashMap<>();
  private Integer timeout = 30;
}
