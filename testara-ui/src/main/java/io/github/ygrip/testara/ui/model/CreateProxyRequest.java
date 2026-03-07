package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>CreateProxyRequest class.</p>
 *
 * @author yunaz.ramadhan on 8/16/2020
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProxyRequest {
  private Integer port;
  private String proxyUsername;
  private String proxyPassword;
  private String bindAddress;
  private String serverBindAddress;
  private boolean useEcc;
  private boolean trustAllServers;
}
