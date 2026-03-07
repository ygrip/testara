package io.github.ygrip.testara.api;

import io.github.ygrip.testara.api.model.CommonResponse;
import io.github.ygrip.testara.api.model.CreateRequestSpecification;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Cookies;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.MultiPartSpecification;
import io.restassured.specification.RequestSpecification;

import java.nio.file.Path;
import java.util.Map;

/**
 * <p>RequestBuilder interface.</p>
 *
 * @author yunaz.ramadhan on 12/6/2019
 * @version $Id: $Id
 */
public interface RequestBuilder {

  /**
   * Check whether request specification has been initialized, if not then initialize it through
   * init() method
   *
   * @throws Exception when there are failure during init() method execution
   */
  void checkRequest() throws Exception;

  /**
   * Public method that can be accessed by client to initialize the request specification
   * refer to https://github.com/rest-assured/rest-assured/wiki/usage
   *
   * @param serviceName as user specified service that want to be build
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  RequestBuilder setService(String serviceName) throws Exception;

  /**
   * Public method for client to add headers to the existing headers it will overwrite existing
   * header with the same key
   *
   * @param headers as user specified headers
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder addHeaders(Map<String, Object> headers) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method for client to add single header to existing request specification
   *
   * @param key   as the header key
   * @param value as the header value
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder addHeader(String key, String value) throws Exception {
    checkRequest();
    return this;
  }


  /**
   * Public method for client to set orm parameters to request specification
   *
   * @param parameters as the user defined form parameters
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder addFormParams(Map<String, Object> parameters) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method for client to add multiple query parameters, will overwrite same query parameter
   *
   * @param parameters as user defined query parameters
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder addQueryParams(Map<String, Object> parameters) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method for client to add single query parameter, will overwrite same query parameter
   *
   * @param key   as query parameter key
   * @param value as query parameter value
   * @return the RequestBuilder
   * @throws Exception when ther are failure during method execution
   */
  default RequestBuilder addQueryParam(String key, Object value) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to set path param in request specification
   *
   * @param key   as the path param key
   * @param value as the path param value
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder setPathParam(String key, Object value) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to add single form parameter to request specification
   *
   * @param key   as form param key
   * @param value as form param value
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder addFormParam(String key, Object value) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to set multipart data in request specification
   *
   * @param key   as multipart data key
   * @param value as multipart data value
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder setMultiPartData(String key, Object value) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to set multipart data in request specification
   *
   * @param multiPartData   as multipart data
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder setMultiPartData(MultiPartSpecification multiPartData) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to set the cookie in request specification
   *
   * @param cookie as user defined cookie
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder setCookie(Cookie cookie) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to set the cookies in request specification
   *
   * @param cookies as user defined cookies
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  default RequestBuilder setCookies(Cookies cookies) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to get the request spec builder
   *
   * @return the request spec builder
   * @throws Exception if any.
   */
  RequestSpecBuilder getSpecification() throws Exception;

  /**
   * Public method to get the request specification
   *
   * @return the request specification
   * @throws Exception if any.
   */
  RequestSpecification buildSpecification() throws Exception;

  /**
   * <p>setBody.</p>
   *
   * @param body a {@link Object} object.
   * @return a {@link io.github.ygrip.testara.api.RequestBuilder} object.
   * @throws Exception if any.
   */
  default RequestBuilder setBody(Object body) throws Exception {
    checkRequest();
    return this;
  }

  /**
   * Public method to process or make REST request from the request specification
   * CommonResponse is to choose whether print the log for the request when enableLogging = true
   * or not when enableLogging = false
   *
   * @param httpMethod as the user specified HTTP Method
   *                   httpMethod must satisfy one of these value :
   *                   GET, POST, PATCH, PUT, DELETE, HEAD, OPTIONS, TRACE, CONNECT
   * @param url        as the user specified url to call
   * @return the Response
   * @throws Exception when there are failure during method execution
   */
  @CommonResponse(enableLogging = true)
  default Response process(String httpMethod, String url) throws Exception {
    checkRequest();
    httpMethod = httpMethod.toUpperCase();
    return null;
  }

  /**
   * Public method to process or make REST request from the request specification
   * CommonResponse is to choose whether print the log for the request when enableLogging = true
   * or not when enableLogging = false
   *
   * @param httpMethod as the user specified HTTP Method
   *                   httpMethod must satisfy one of these value :
   *                   GET, POST, PATCH, PUT, DELETE, HEAD, OPTIONS, TRACE
   * @param url        as the user specified url to call
   * @return the Response
   * @throws Exception when there are failure during method execution
   */
  @CommonResponse(enableLogging = true)
  default Response process(Method httpMethod, String url) throws Exception {
    return null;
  }

  /**
   * Public method to process or make REST request from the request specification
   * CommonResponse is to choose whether print the log for the request when enableLogging = true
   * or not when enableLogging = false
   *
   * @param specification is the specification user setup
   * @return the Response
   * @throws Exception when there are failure during method execution
   */
  @CommonResponse
  default Response process(CreateRequestSpecification specification) throws Exception {
    checkRequest();
    return null;
  }

  /**
   * This method will load the json file provided in requestSpecificationPath
   * the value will then converted to CreateRequestSpecification object
   * and passed to makeApiCall(CreateRequestSpecification request) method
   *
   * @param requestSpecificationPath is the path of json file of request specification is stored
   * @return Response object type from rest assured
   * @throws java.lang.Exception when there are failure during method execution
   */
  @CommonResponse
  default Response process(String requestSpecificationPath) throws Exception {
    checkRequest();
    return null;
  }

  /**
   * Set request specification content type
   *
   * @param contentType is ContentType as input of the desired content type
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  RequestBuilder setRequestContentType(ContentType contentType) throws Exception;

  /**
   * Set request specification content type
   *
   * @param contentType is String as input of the desired content type
   * @return the RequestBuilder
   * @throws Exception when there are failure during method execution
   */
  RequestBuilder setRequestContentType(String contentType) throws Exception;

  /**
   * Clear all caches
   */
  void clearAllCaches();
}
