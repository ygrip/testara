package io.github.ygrip.testara.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Coverage for {@link TestConfigurationLoader#resolveConfigurationLocation(String)}: the shared
 * core method that both the JUnit4 ({@code TestaraObjectFactory}) and JUnit5
 * ({@code TestaraFrameworkExtension}) engines defer to so {@code -Dtestara.configuration.location}
 * is resolved identically regardless of which engine initializes the framework.
 * <p>
 * Runs {@link ExecutionMode#SAME_THREAD}: every test here mutates the global, process-wide
 * {@code testara.configuration.location}/{@code configuration.location} system properties, which
 * is not safe under this module's default concurrent-methods-within-a-class execution mode.
 */
@Execution(ExecutionMode.SAME_THREAD)
class TestConfigurationLoaderTests {

  @AfterEach
  void clearSystemProperties() {
    System.clearProperty("testara.configuration.location");
    System.clearProperty("configuration.location");
  }

  @Test
  void explicitValueWinsOverSystemProperty() {
    System.setProperty("testara.configuration.location", "classpath:from-system-property.properties");

    String resolved = TestConfigurationLoader.resolveConfigurationLocation("classpath:from-explicit.properties");

    assertThat(resolved, equalTo("classpath:from-explicit.properties"));
    assertThat(System.getProperty("configuration.location"), equalTo("classpath:from-explicit.properties"));
  }

  @Test
  void systemPropertyUsedWhenExplicitIsNull() {
    System.setProperty("testara.configuration.location", "classpath:from-system-property.properties");

    String resolved = TestConfigurationLoader.resolveConfigurationLocation(null);

    assertThat(resolved, equalTo("classpath:from-system-property.properties"));
    assertThat(System.getProperty("configuration.location"), equalTo("classpath:from-system-property.properties"));
  }

  @Test
  void systemPropertyUsedWhenExplicitIsBlank() {
    System.setProperty("testara.configuration.location", "classpath:from-system-property.properties");

    String resolved = TestConfigurationLoader.resolveConfigurationLocation("   ");

    assertThat(resolved, equalTo("classpath:from-system-property.properties"));
  }

  @Test
  void fallsBackToDefaultGlobWhenNeitherExplicitNorSystemPropertyIsSet() {
    String resolved = TestConfigurationLoader.resolveConfigurationLocation(null);

    assertThat(resolved, equalTo("classpath:*.properties"));
    assertThat(System.getProperty("configuration.location"), equalTo("classpath:*.properties"));
  }

  @Test
  void setsConfigurationLocationSystemPropertyAsASideEffect() {
    System.clearProperty("configuration.location");

    TestConfigurationLoader.resolveConfigurationLocation("classpath:explicit-side-effect.properties");

    assertThat(System.getProperty("configuration.location"), equalTo("classpath:explicit-side-effect.properties"));
  }
}
