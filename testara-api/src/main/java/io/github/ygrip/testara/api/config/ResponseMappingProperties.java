package io.github.ygrip.testara.api.config;

import io.github.ygrip.testara.api.model.ResponseMappingModel;
import io.github.ygrip.testara.core.config.LoadProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>ResponseMappingProperties class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@LoadProperties(prefix = "response")
public class ResponseMappingProperties {
  private boolean reportAdditionalData = true;
  private ResponseMappingModel defaultFields = new ResponseMappingModel();
  private Map<String, ResponseMappingModel> fields = new HashMap<>();
}
