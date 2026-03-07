package io.github.ygrip.testara.api.interceptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class RequestInterceptorLoader {
  private RequestInterceptorLoader() {

  }

  public static List<Class<? extends RequestInterceptor>> loads() {
    ServiceLoader<RequestInterceptor> loader =
        ServiceLoader.load(RequestInterceptor.class, Thread.currentThread().getContextClassLoader());

    List<Class<? extends RequestInterceptor>> results = new ArrayList<>();

    loader.stream().forEach(provider -> {
      results.add(provider.type());
    });

    return results;
  }
}
