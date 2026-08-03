package io.github.ygrip.testara.core.config;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.util.ServiceLoader;

@Log4j2
public final class TestConfigurationLoader {

  private static final String DEFAULT_CONFIGURATION_LOCATION = "classpath:*.properties";

  private TestConfigurationLoader() {

  }

  public static TestConfiguration load() {
    return ServiceLoader.load(TestConfiguration.class, Thread.currentThread().getContextClassLoader())
        .findFirst()
        .orElse(new DefaultConfiguration());
  }

  /**
   * Resolves the effective configuration-file location list: an explicit value takes precedence,
   * otherwise falls back to the {@code testara.configuration.location} system property, otherwise
   * the default classpath wildcard glob. Sets the {@code configuration.location} system property
   * that {@link PropertiesPropertySource} consumes, so both the JUnit4 and JUnit5 engines resolve
   * configuration location identically regardless of which one initializes the framework.
   */
  public static String resolveConfigurationLocation(String explicit) {
    String location = StringUtils.isNotBlank(explicit) ? explicit : System.getProperty("testara.configuration.location");
    if (StringUtils.isBlank(location)) {
      location = DEFAULT_CONFIGURATION_LOCATION;
    }
    System.setProperty("configuration.location", location);
    log.debug("Resolved configuration location: {}", location);
    return location;
  }
}
