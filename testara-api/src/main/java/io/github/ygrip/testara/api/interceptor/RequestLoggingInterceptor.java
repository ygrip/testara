package io.github.ygrip.testara.api.interceptor;

import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.model.RequestLog;
import io.github.ygrip.testara.core.context.TestFramework;
import io.restassured.specification.RequestSpecification;

import java.util.Set;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

public final class RequestLoggingInterceptor implements RequestInterceptor {
  private final boolean ENABLE_REQUEST_LOG;
  private Set<RequestLog> requestLogging;

  public RequestLoggingInterceptor() {
    ApiProperties apiConfig = TestFramework.context().configuration().get(ApiProperties.class);
    ENABLE_REQUEST_LOG = apiConfig.getEnableRequestLog();
    requestLogging = apiConfig.getRequestLogging();
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }

  @Override
  public RequestInterceptor logs(Set<RequestLog> logLevels) {
    if (!isBlank(logLevels)) {
      this.requestLogging = logLevels;
    }
    return this;
  }

  @Override
  public void logic(RequestSpecification specification) {
    logRequest(specification);
  }

  private void logRequest(RequestSpecification specification) {
    if (this.ENABLE_REQUEST_LOG) {
      printRequestLog(this.requestLogging, specification);
    }
  }

  private void printRequestLog(Set<RequestLog> logs, RequestSpecification specification) {
    if (!isBlank(logs) && !isBlank(specification)) {
      if (logs.contains(RequestLog.ALL)) {
        specification.log().all();
      } else {
        for (RequestLog log : logs) {
          if (log.equals(RequestLog.METHOD)) {
            specification.log().method();
          } else if (log.equals(RequestLog.PATH)) {
            specification.log().uri();
          } else if (log.equals(RequestLog.COOKIES)) {
            specification.log().cookies();
          } else if (log.equals(RequestLog.HEADERS)) {
            specification.log().headers();
          } else if (log.equals(RequestLog.PARAMS)) {
            specification.log().params();
          } else if (log.equals(RequestLog.BODY)) {
            specification.log().body();
          }
        }
      }
    }
  }
}
