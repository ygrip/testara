package io.github.ygrip.testara.spring.config;

import io.github.ygrip.testara.core.config.PropertySource;
import io.github.ygrip.testara.spring.context.SpringContextHolder;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * PropertySource implementation that bridges Spring Environment to testara-core.
 * <p>
 * This allows properties defined in Spring (application.properties, YAML, etc.)
 * to be available through testara's PropertyResolver.
 * <p>
 * Priority is set high (1000) to ensure Spring properties take precedence.
 */
@Log4j2
public final class SpringPropertySource implements PropertySource {

  private static final int SPRING_PROPERTY_PRIORITY = 1000;

  @Override
  public int priority() {
    return SPRING_PROPERTY_PRIORITY;
  }

  @Override
  public Map<String, String> load(Map<String, String> existingProperties) {
    ApplicationContext ctx = SpringContextHolder.getApplicationContext();
    if (ctx == null) {
      log.debug("Spring ApplicationContext not available, skipping Spring property loading");
      return combine(new HashMap<>(), existingProperties);
    }

    Environment env = ctx.getEnvironment();
    Map<String, String> springProperties = extractProperties(env);
    
    log.debug("Loaded {} properties from Spring Environment", springProperties.size());
    
    // Spring properties take precedence over existing properties
    return combine(springProperties, existingProperties);
  }

  /**
   * Extract all enumerable properties from Spring Environment.
   */
  private Map<String, String> extractProperties(Environment env) {
    Map<String, String> result = new HashMap<>();

    if (!(env instanceof ConfigurableEnvironment configEnv)) {
      log.debug("Environment is not ConfigurableEnvironment, cannot enumerate properties");
      return result;
    }

    MutablePropertySources sources = configEnv.getPropertySources();
    sources.forEach(source -> {
      if (source instanceof EnumerablePropertySource<?> enumerable) {
        for (String name : enumerable.getPropertyNames()) {
          try {
            Object value = enumerable.getProperty(name);
            if (value != null) {
              result.put(name, value.toString());
            }
          } catch (Exception e) {
            log.trace("Could not read property '{}' from source '{}': {}",
                name, source.getName(), e.getMessage());
          }
        }
      }
    });

    return result;
  }
}
