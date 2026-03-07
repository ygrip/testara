package io.github.ygrip.testara.api.interceptor;

import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.model.ResponseLog;
import io.github.ygrip.testara.core.context.TestFramework;
import io.restassured.response.Response;

import java.util.Set;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

public final class ResponseLoggingInterceptor implements ResponseInterceptor {
  private final boolean ENABLE_RESPONSE_LOG;
  private Set<ResponseLog> responseLogging;

  public ResponseLoggingInterceptor() {
    ApiProperties apiConfig = TestFramework.context().configuration().get(ApiProperties.class);
    ENABLE_RESPONSE_LOG = apiConfig.getEnableResponseLog();
    responseLogging = apiConfig.getResponseLogging();
  }


  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }

  @Override
  public ResponseInterceptor logs(Set<ResponseLog> logLevels) {
    if (!isBlank(logLevels)) {
      this.responseLogging = logLevels;
    }
    return this;
  }

  @Override
  public void logic(Response response) {
    if (this.ENABLE_RESPONSE_LOG) {
      printResponseLog(this.responseLogging, response);
    }
  }

  private void printResponseLog(Set<ResponseLog> logs, Response response) {
    if (!isBlank(logs) && !isBlank(response) && !isBlank(response.getBody())) {
      System.out.println("Response :");
      if (logs.contains(ResponseLog.ALL)) {
        response.then().log().all();
      } else {
        for (ResponseLog log : logs) {
          if (log.equals(ResponseLog.STATUS)) {
            response.then().log().status();
          } else if (log.equals(ResponseLog.COOKIES)) {
            response.then().log().cookies();
          } else if (log.equals(ResponseLog.HEADERS)) {
            response.then().log().headers();
          } else if (log.equals(ResponseLog.BODY)) {
            response.then().log().body();
          }
        }
      }
    }
  }
}
