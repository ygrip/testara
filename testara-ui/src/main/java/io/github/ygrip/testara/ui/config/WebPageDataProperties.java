package io.github.ygrip.testara.ui.config;

import java.util.HashMap;
import java.util.Map;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.WebPageData;

import lombok.Data;

/**
 * <p>WebPageDataProperties class.</p>
 *
 * @author yunaz.ramadhan on 5/18/2020
 * @version $Id: $Id
 */
@Data
@LoadProperties(prefix = "web")
public class WebPageDataProperties {
  private Map<DeviceType, Map<String, WebPageData>> page = new HashMap<>();
}
