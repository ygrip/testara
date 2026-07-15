package io.github.ygrip.testara.engine.option;

import io.github.ygrip.testara.core.config.PropertyResolver;
import io.github.ygrip.testara.core.config.PropertyResolverLoader;
import org.junit.platform.engine.ConfigurationParameters;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Layers the JUnit Platform {@link ConfigurationParameters} (junit-platform.properties +
 * system properties) over testara-core's raw {@link PropertyResolver#sourceProperties()} -
 * the same {@code PropertySource} chain (classpath:*.properties scan, incl. cucumber.properties,
 * plus env/system properties) already used to back {@code properties(key)} in feature files.
 * <p>
 * Deliberately uses {@code sourceProperties()} rather than {@code TestConfiguration.get(key)}:
 * the latter goes through {@code CommandPatternPropertyResolver}, which eagerly evaluates
 * command-expression values (e.g. {@code properties(...)}, {@code random(...)}) via
 * {@code CommandExecutor}, and that requires a live per-scenario {@code TestContext} which does
 * not exist yet at JUnit5 discovery time. {@code sourceProperties()} returns the raw,
 * pre-command-evaluation string map, so no TestContext dependency here.
 * <p>
 * junit-platform.properties always wins on key collisions, so JUnit5-only overrides
 * (parallelism, naming strategy, retry, ...) still take precedence, while glue/features/tags
 * can live once in cucumber.properties - the file IDE Cucumber plugins and other classic
 * Cucumber tooling already read.
 */
public final class TestaraConfigurationParameters {

  private TestaraConfigurationParameters() {
  }

  public static ConfigurationParameters merge(ConfigurationParameters primary) {
    Map<String, String> fallback = PropertyResolverLoader.load().sourceProperties();
    if (fallback.isEmpty()) {
      return primary;
    }
    return new Layered(primary, fallback);
  }

  private static final class Layered implements ConfigurationParameters {
    private final ConfigurationParameters primary;
    private final Map<String, String> fallback;

    private Layered(ConfigurationParameters primary, Map<String, String> fallback) {
      this.primary = primary;
      this.fallback = fallback;
    }

    @Override
    public Optional<String> get(String key) {
      Optional<String> value = primary.get(key);
      if (value.isPresent()) {
        return value;
      }
      return Optional.ofNullable(fallback.get(key));
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
      return get(key).map(Boolean::parseBoolean);
    }

    @Override
    public Set<String> keySet() {
      Set<String> keys = new LinkedHashSet<>(fallback.keySet());
      keys.addAll(primary.keySet());
      return keys;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int size() {
      return keySet().size();
    }
  }
}
