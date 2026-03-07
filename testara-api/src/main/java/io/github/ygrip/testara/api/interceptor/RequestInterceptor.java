package io.github.ygrip.testara.api.interceptor;

import io.github.ygrip.testara.api.model.InterceptorExecutionMode;
import io.github.ygrip.testara.api.model.RequestLog;
import io.github.ygrip.testara.core.context.TestContext;
import io.restassured.specification.RequestSpecification;

import java.time.Duration;
import java.util.Set;

public interface RequestInterceptor {
  default int priority() {
    return 0;
  }

  default RequestInterceptor logs(Set<RequestLog> logLevels) {
    return this;
  }

  default RequestInterceptor context(TestContext context) {
    return this;
  }

  default InterceptorExecutionMode executionMode() {
    return InterceptorExecutionMode.SYNC;
  }

  default Duration timeout() {
    return Duration.ofSeconds(2);
  }

  default void intercept(RequestSpecification specification) {
    try {
      switch (executionMode()) {
        case SYNC -> {
          logic(specification);
        }
        case ASYNC -> {
          InterceptorExecutor.executor().execute(() -> InterceptorRunner.run(() -> logic(specification), timeout()));
        }
        case null, default -> {

        }
      }
    } catch (Exception ignored) {

    }
  }

  void logic(RequestSpecification specification);
}
