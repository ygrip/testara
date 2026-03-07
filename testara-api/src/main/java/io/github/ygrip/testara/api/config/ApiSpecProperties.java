package io.github.ygrip.testara.api.config;

import io.github.ygrip.testara.api.model.DefaultApiSpec;
import io.github.ygrip.testara.core.config.LoadProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>ApiSpecProperties class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@LoadProperties(prefix = "spec")
public class ApiSpecProperties {
  private Map<String, DefaultApiSpec> api = new HashMap<>();
}
