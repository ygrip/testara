package io.github.ygrip.testara.api.model;

import io.restassured.http.Method;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>CreateRequestSpecification class.</p>
 *
 * @author yunaz.ramadhan on 2/16/2021
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRequestSpecification {
  private String specification;
  private Method httpMethod;
  private String url;
  private String contentType;
  private List<CookieModel> cookies;
  private Map<String, Object> queryParameters;
  private Map<String, Object> formParameters;
  private Map<String, Object> headers;
  private Map<String, Object> pathParameters;
  private Map<String, Object> multiPartData;
  private Object payload;
  private Set<RequestLog> requestLog;
  private Set<ResponseLog> responseLog;
  private boolean autoCloseConnection = false;
}
