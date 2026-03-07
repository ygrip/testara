package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.factory.ObjectFactory;

public interface TestContext {
  ObjectFactory factory();

  ObjectConverter converter();

  TestConfiguration configuration();

  <T> T get(Class<T> type);

  boolean has(Class<?> type);
}
