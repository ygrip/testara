package io.github.ygrip.testara.ui.model;

import java.util.Map;

import lombok.Data;

/**
 * <p>ApiModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class ApiModel {
  private String host;
  private Integer port;
  private String basePath;
  private Map<String, Object> header;
  private Map<String, Object> parameter;
  private Map<String, Object> form_param;
  private String default_specification;
  private boolean followRedirects = false;
  private int maxRedirect = 0;
  private boolean reuseHttpClientInstance = false;
  private boolean useBasicAuthentication = false;
  private String username;
  private String password;
  private boolean usePreemptiveAuthentication = false;
  private boolean applyDefaultContentIfUndefined = false;
  private boolean autoCloseIdleConnection = true;
  private ProxyModel proxy = null;
}
