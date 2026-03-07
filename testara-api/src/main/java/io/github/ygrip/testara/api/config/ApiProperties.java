package io.github.ygrip.testara.api.config;

import io.github.ygrip.testara.api.model.ApiModel;
import io.github.ygrip.testara.api.model.RequestLog;
import io.github.ygrip.testara.api.model.ResponseLog;
import io.github.ygrip.testara.core.config.LoadProperties;
import lombok.Data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
@LoadProperties(prefix = "api")
public class ApiProperties {
  private Set<RequestLog> requestLogging = Collections.singleton(RequestLog.ALL);
  private Set<ResponseLog> responseLogging = Collections.singleton(ResponseLog.BODY);
  private Boolean enableRequestLog = true;
  private Boolean enableResponseLog = true;
  private Map<String, ApiModel> service = new HashMap<>();
  
  // File logging configuration (for RequestBuilderImpl interceptors)
  private Boolean enableFileLogging = false;
  private Integer fileLoggingMaxBodySize = 10000;
  
  // Load test logging configuration (for ConcurrentRequestBuilder)
  private Boolean loadTestEnableFileLogging = false;
  private Boolean loadTestEnableConsoleLogging = false;
  private Boolean loadTestLogRequests = true;
  private Boolean loadTestLogResponses = true;
  private Integer loadTestLogBodyMaxSize = 10000;
  private String loadTestLogOutputDir = "target/load-test-logs";
}
