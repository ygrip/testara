package io.github.ygrip.testara.core.converter;

import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public final class ObjectConverterLoader {
  private static volatile ObjectConverter INSTANCE;

  private ObjectConverterLoader() {

  }

  public static ObjectConverter instance() {
    if (INSTANCE == null) {
      ServiceLoader<ObjectConverter> loader = ServiceLoader.load(
        ObjectConverter.class,
        Thread.currentThread()
          .getContextClassLoader()
      );

      INSTANCE = StreamSupport.stream(loader.spliterator(), false)
        .max(Comparator.comparingInt(ObjectConverter::priority))
        .orElse(new NestedObjectConverter());
    }
    return INSTANCE;
  }
}
