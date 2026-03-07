package io.github.ygrip.testara.ui.model;

import java.net.URL;

import lombok.Data;

/**
 * <p>TestaraProxyModel class.</p>
 *
 * @author yunaz.ramadhan on 8/16/2020
 * @version $Id: $Id
 */
@Data
public class TestaraProxyModel {
  private String protocol;
  private String proxyAddress;
  private String proxyHost;
  private Integer proxyPort;

  /**
   * <p>Constructor for TestaraProxyModel.</p>
   *
   * @param proxyAddress a {@link String} object.
   */
  public TestaraProxyModel(String proxyAddress) {
    try {
      URL url = new URL(proxyAddress);
      this.protocol = url.getProtocol();
      this.proxyHost = url.getHost();
      this.proxyPort = url.getPort() < 0 ? 80 : url.getPort();
      if (url.getPort() == -1) {
        this.proxyAddress = String.format("%s://%s", this.protocol, this.proxyHost);
      } else {
        this.proxyAddress =
            String.format("%s://%s:%d", this.protocol, this.proxyHost, this.proxyPort);
      }
    } catch (Exception ignored) {

    }
  }
}
