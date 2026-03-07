package io.github.ygrip.testara.api.interceptor;

import io.github.ygrip.testara.api.model.InterceptorExecutionMode;
import io.github.ygrip.testara.api.model.ResponseLog;
import io.github.ygrip.testara.core.context.TestContext;
import io.restassured.response.Response;

import java.time.Duration;
import java.util.Set;

public interface ResponseInterceptor {
  default int priority() {
    return 0;
  }

  default ResponseInterceptor service(String serviceName) {
    return this;
  }

  default ResponseInterceptor logs(Set<ResponseLog> logLevels) {
    return this;
  }

  default InterceptorExecutionMode executionMode() {
    return InterceptorExecutionMode.SYNC;
  }

  default Duration timeout() {
    return Duration.ofSeconds(2);
  }

  default ResponseInterceptor context(TestContext context) {
    return this;
  }

  default void intercept(Response response) {
    try {
      switch (executionMode()) {
        case SYNC -> {
          logic(response);
        }
        case ASYNC -> {
          InterceptorExecutor.executor().execute(() -> InterceptorRunner.run(() -> logic(response), timeout()));
        }
        case null, default -> {

        }
      }
    } catch (Exception ignored) {

    }
  }

  void logic(Response response);
}
