package io.github.ygrip.testara.ui.appium.model;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * <p>AppsData class.</p>
 *
 * @author yunaz.ramadhan on 6/10/2020
 * @version $Id: $Id
 */
@Data
@Builder
public class AppsData {
  private String appName;
  private String fileName;
  private String fileLocation;
  private String appPackage;
  private Map<String, String> capabilities;
  private boolean resetInstall;
}
