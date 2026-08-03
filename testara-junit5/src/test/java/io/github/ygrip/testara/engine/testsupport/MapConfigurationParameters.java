package io.github.ygrip.testara.engine.testsupport;

import org.junit.platform.engine.ConfigurationParameters;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal {@link ConfigurationParameters} test double backed by a plain {@link Map}, so tests
 * can construct a {@code TestaraCucumberEngineOptions}/{@code TestaraExtensionContext} without a
 * real JUnit5 launcher session.
 */
public final class MapConfigurationParameters implements ConfigurationParameters {

  private final Map<String, String> values;

  public MapConfigurationParameters(Map<String, String> values) {
    this.values = values;
  }

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(values.get(key));
  }

  @Override
  public Optional<Boolean> getBoolean(String key) {
    return get(key).map(Boolean::parseBoolean);
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public Set<String> keySet() {
    return values.keySet();
  }
}
