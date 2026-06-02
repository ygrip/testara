package io.github.ygrip.testara.ui.model;

import io.github.ygrip.testara.ui.driver.AbstractDriver;

import lombok.Builder;
import lombok.Data;

/**
 * <p>TestaraDriverInfo class.</p>
 *
 * @author yunaz.ramadhan on 4/10/2021
 * @version $Id: $Id
 */
@Data
@Builder
public class TestaraDriverInfo {
  private String fullDriverName;
  private String driverName;
  private String deviceDownloadLocation;
  private DeviceType platform;
  private boolean useProxy;
  private boolean remoteDriver;
  private AvailableProxy proxyType;
  private Class<? extends AbstractDriver<?, ?>> classReference;
}
