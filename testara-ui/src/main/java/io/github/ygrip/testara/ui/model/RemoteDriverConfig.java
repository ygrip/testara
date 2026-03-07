package io.github.ygrip.testara.ui.model;

import lombok.Data;

/**
 * <p>RemoteDriverProperties class.</p>
 *
 * @author yunaz.ramadhan on 2/29/2020
 * @version $Id: $Id
 */
@Data
public class RemoteDriverConfig {
  private String uri;
  private boolean enabled;
  private boolean enableVnc;
  private boolean enableVideo;
  private boolean startServer;
  private boolean stopServer;
  
  /**
   * Connection timeout in seconds for remote driver initialization.
   * Default: 30 seconds
   */
  private int connectionTimeoutSeconds = 30;
  
  /**
   * Read timeout in seconds for remote driver operations.
   * Default: 60 seconds
   */
  private int readTimeoutSeconds = 60;
  
  /**
   * Maximum time in seconds to wait for remote driver session creation.
   * Default: 120 seconds
   */
  private int sessionCreationTimeoutSeconds = 120;
}
