package io.github.ygrip.testara.properties.consul;

import io.github.ygrip.testara.core.config.PropertySource;
import io.github.ygrip.testara.core.model.PlaceholderLookup;
import io.github.ygrip.testara.properties.config.BootstrapPropertySource;
import io.github.ygrip.testara.properties.model.ConsulProperties;
import io.github.ygrip.testara.properties.support.ConsulHelper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

@Log4j2
public final class ConsulPropertySource extends BootstrapPropertySource implements PropertySource {
  private final PlaceholderLookup LOOKUP;
  private volatile static ConsulHelper consul;

  public ConsulPropertySource() {
    LOOKUP = key -> {
      String result = null;
      String v = System.getenv(key);
      if (v != null) {
        result = v;
      } else {
        v = System.getProperty(key);
        if (v != null) {
          result = v;
        }
      }
      return result;
    };
  }

  private ConsulHelper getConsul(Map<String, String> properties) {
    if (ObjectUtils.isEmpty(consul)) {

      boolean enabled = Boolean.parseBoolean(resolve(get("config.consul.enabled", properties), LOOKUP));

      if (enabled) {
        String host = resolve(require("config.consul.host", properties), LOOKUP);
        int port = Integer.parseInt(resolve(require("config.consul.port", properties), LOOKUP));
        String prefix = resolve(require("config.consul.prefix", properties), LOOKUP);
        String aclToken = resolve(get("config.consul.acl-token", properties), LOOKUP);
        prefix = prefix.endsWith("/") ? prefix : prefix + "/";
        consul = new ConsulHelper(ConsulProperties.builder()
            .mapped(new HashMap<>())
            .aclToken(aclToken)
            .prefix(prefix)
            .enabled(true)
            .port(port)
            .host(host)
            .build());
      } else {
        consul = new ConsulHelper(ConsulProperties.builder().enabled(false).build());
      }
    }
    return consul;
  }

  @Override
  public int priority() {
    return 3;
  }

  @Override
  public Map<String, String> load(Map<String, String> properties) {
    Map<String, String> consulProperties = getConsul(properties).getValues();
    return combine(consulProperties, properties);
  }
}
