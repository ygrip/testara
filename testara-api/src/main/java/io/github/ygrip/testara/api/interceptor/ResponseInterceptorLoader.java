package io.github.ygrip.testara.api.interceptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ResponseInterceptorLoader {
  private ResponseInterceptorLoader() {

  }

  public static List<Class<? extends ResponseInterceptor>> loads() {
    ServiceLoader<ResponseInterceptor> loader =
        ServiceLoader.load(ResponseInterceptor.class, Thread.currentThread().getContextClassLoader());

    List<Class<? extends ResponseInterceptor>> results = new ArrayList<>();

    loader.stream().forEach(provider -> {
      results.add(provider.type());
    });

    return results;
  }
}
