package io.github.ygrip.testara.ui.context;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.config.EngineProperties;
import io.github.ygrip.testara.ui.factory.EngineFactory;
import io.github.ygrip.testara.ui.registry.UiAutomationFactory;

public final class TestUI {
  private TestUI() {

  }

  public static <T extends EngineFactory<?>> T with(Class<T> engine) {
    return UiAutomationFactory.forEngine(engine);
  }

  @SuppressWarnings("unchecked")
  public static <T extends EngineFactory<?>> T with(String engineName) {
    return (T) UiAutomationFactory.forEngine(engineName);
  }

  @SuppressWarnings("unchecked")
  public static <T extends EngineFactory<?>> T withDefaultEngine() {
    final var properties = TestFramework.configuration()
      .get(EngineProperties.class);
    final var engineName = Optional.ofNullable(properties)
      .map(EngineProperties::getDefaultEngine)
      .filter(StringUtils::isNotBlank)
      .orElse(null);
    return (T) UiAutomationFactory.forEngine(engineName);
  }
}
