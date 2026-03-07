package io.github.ygrip.testara.api.interceptor;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.api.config.ResponseMappingProperties;
import io.github.ygrip.testara.api.data.ApiResponseData;
import io.github.ygrip.testara.api.model.CommonResponseModel;
import io.github.ygrip.testara.api.model.ResponseMappingModel;
import io.github.ygrip.testara.core.context.TestContext;

import io.restassured.http.ContentType;
import io.restassured.http.Cookies;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class StoreResponseInterceptor implements ResponseInterceptor {
  private String serviceName;
  private TestContext context;

  @Override
  public ResponseInterceptor context(TestContext context) {
    this.context = context;
    return this;
  }

  @Override
  public ResponseInterceptor service(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  @Override
  public void logic(Response response) {
    storeResponse(response);
  }

  private void clearResponse() {
    ApiResponseData responseHolder = context.get(ApiResponseData.class);
    responseHolder.clearData();
  }

  private void storeResponse(Response result) {
    if (result == null) {
      log.warn("Latest response is empty, clearing response data");
      clearResponse();
    } else {
      try {
        log.debug("Try snapshot response data");
        snapShotResponse(result, serviceName);
      } catch (Exception ignored) {
        log.warn("Unable to process response data, latest response data will be cleared");
        clearResponse();
      }
    }
  }

  private ResponseMappingModel getResponseMappingModel(String serviceName) {
    ResponseMappingProperties props = context.get(ResponseMappingProperties.class);
    return ObjectUtils.isEmpty(props) ?
      new ResponseMappingModel() :
      StringUtils.isNotBlank(serviceName) ?
        props.getFields()
          .getOrDefault(serviceName, props.getDefaultFields()) :
        props.getDefaultFields();
  }

  private void snapShotResponse(Response response, String currentService) {
    final ResponseMappingModel mappingModel = getResponseMappingModel(currentService);

    Object body;
    long responseTimeMillis = response != null ? response.time() : 0L;
    int statusCode = response == null ? 500 : response.getStatusCode();
    ContentType contentType = response == null ?
      ContentType.ANY :
      ContentType.fromContentType(response.getContentType()) == null ?
        ContentType.ANY :
        ContentType.fromContentType(response.getContentType());
    boolean success;
    String errorMessage = null;
    String errorCode = null;
    if (contentType.equals(ContentType.JSON)) {
      try {
        body = response.getBody() == null ?
          null :
          response.getBody()
            .as(Object.class);
      } catch (Exception ignored) {
        log.warn("Unable to get content from response");
        body = null;
      }
      try {
        success = response.then()
          .extract()
          .body()
          .jsonPath()
          .getBoolean(mappingModel.getSuccess());
      } catch (Exception ignored) {
        success = statusCode / 100 == 2;
      }
      try {
        errorMessage = response.then()
          .extract()
          .body()
          .jsonPath()
          .getString(mappingModel.getErrorMessage());
      } catch (Exception ignored) {
      }
      try {
        errorCode = response.then()
          .extract()
          .body()
          .jsonPath()
          .getString(mappingModel.getErrorCode());
      } catch (Exception ignored) {
      }
    } else if (contentType.equals(ContentType.XML)) {
      try {
        body = response.getBody() == null ?
          null :
          response.getBody()
            .as(Object.class);
      } catch (Exception ignored) {
        log.warn("Unable to get content from response");
        body = null;
      }
      try {
        success = response.then()
          .extract()
          .body()
          .xmlPath()
          .getBoolean(mappingModel.getSuccess());
      } catch (Exception ignored) {
        success = statusCode / 100 == 2;
      }
      try {
        errorMessage = response.then()
          .extract()
          .body()
          .xmlPath()
          .getString(mappingModel.getErrorMessage());
      } catch (Exception ignored) {
      }
      try {
        errorCode = response.then()
          .extract()
          .body()
          .xmlPath()
          .getString(mappingModel.getErrorCode());
      } catch (Exception ignored) {
      }
    } else {
      try {
        body = response == null || response.getBody() == null ?
          null :
          response.getBody()
            .asString();
      } catch (Exception ignored) {
        log.warn("Unable to get content from response");
        body = null;
      }
      success = statusCode / 100 == 2;
      errorCode = response == null ? "ERROR" : null;
      errorMessage = response == null ? "Cannot map error message from response" : null;
    }
    Cookies cookies = new Cookies();
    Headers headers = new Headers();
    String sessionId = null;
    try {
      cookies = response != null ? response.getDetailedCookies() : cookies;
    } catch (Exception ignored) {
      log.debug("Unable to get cookies from response");
    }
    try {
      headers = response != null ? response.getHeaders() : headers;
    } catch (Exception ignored) {
      log.debug("Unable to get headers from response");
    }
    try {
      sessionId = response != null ? response.getSessionId() : null;
    } catch (Exception ignored) {
      log.debug("Unable to get session id from response");
    }
    ApiResponseData responseHolder = context.get(ApiResponseData.class);
    responseHolder.setErrorMessage(errorMessage);
    responseHolder.setStatusCode(statusCode);
    responseHolder.setErrorCode(errorCode);
    responseHolder.setSuccess(success);
    responseHolder.setData(CommonResponseModel.builder()
      .responseTimeMillis(responseTimeMillis)
      .contentType(contentType)
      .sessionId(sessionId)
      .cookies(cookies)
      .headers(headers)
      .body(body)
      .build());
  }
}
