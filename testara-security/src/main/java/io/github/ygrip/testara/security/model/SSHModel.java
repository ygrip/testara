package io.github.ygrip.testara.security.model;

import lombok.Data;

/**
 * <p>SSHModel class.</p>
 *
 * @author yunaz.ramadhan on 6/10/2021
 * @version $Id: $Id
 */
@Data
public class SSHModel {
  private String host;
  private Integer port;
  private Integer sessionTimeout = 10000;
  private Integer channelTimeout = 10000;
  private String username;
  private String password;
  private String privateKey;
  private String passPhrase;
  private String knownHostLocation;
  private SSHAuthenticationType method = SSHAuthenticationType.PASSWORD;
  private SSHChannelType channelType = SSHChannelType.SESSION;
  private boolean strictHostKeyCheckingEnabled;
}
