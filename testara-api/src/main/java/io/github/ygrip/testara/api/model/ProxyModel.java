package io.github.ygrip.testara.api.model;

import lombok.Data;

/**
 * <p>ProxyModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class ProxyModel {
  private String host;
  private Integer port;
  private String scheme = "http";
  private boolean withAuthentication = false;
  private String username;
  private String password;
}
