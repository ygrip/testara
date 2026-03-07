package io.github.ygrip.testara.security.config;

import java.util.HashMap;
import java.util.Map;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.security.model.SSHModel;

import lombok.Data;

/**
 * <p>SSHProperties class.</p>
 *
 * @author yunaz.ramadhan on 6/10/2021
 * @version $Id: $Id
 */
@Data
@LoadProperties(prefix = "ssh")
public class SSHProperties {
  private Map<String, SSHModel> config = new HashMap<>();
}
