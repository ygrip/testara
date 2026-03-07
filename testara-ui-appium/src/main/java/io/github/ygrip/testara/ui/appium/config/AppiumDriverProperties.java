package io.github.ygrip.testara.ui.appium.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.ui.appium.model.AppsData;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.EmulationModel;
import io.github.ygrip.testara.ui.model.RemoteDriverConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@LoadProperties(prefix = "appium.driver")
public class AppiumDriverProperties extends AbstractDriverProperties {
  private boolean enableWebSocket = true;
  private boolean maximizeBrowser = true;
  private boolean clearCache = false;
  private Duration timeout = Duration.ofSeconds(60);
  private Duration installTimeout = Duration.ofSeconds(120);
  private Map<DeviceType, Map<String, String>> userAgent = new HashMap<>();
  private Map<DeviceType, Map<String, AppsData>> apps = new HashMap<>();
  private Map<DeviceType, Map<String, String>> binaryPath = new HashMap<>();
  private Map<DeviceType, Map<String, Map<String, Object>>> capabilities = new HashMap<>();
  private Map<DeviceType, Map<String, List<String>>> args = new HashMap<>();
  private Map<String, String> version = new HashMap<>();
  private Map<DeviceType, RemoteDriverConfig> remoteDriver = new HashMap<>();
  private Map<DeviceType, Map<String, EmulationModel>> emulation = new HashMap<>();


  public AppsData getAppsData(DriverSession<?> session, String name) {
    final var deviceType = session.platform();
    return Optional.ofNullable(apps)
      .map(app -> app.get(deviceType))
      .filter(ObjectUtils::isNotEmpty)
      .map(app -> app.get(name))
      .filter(ObjectUtils::isNotEmpty)
      .orElse(null);
  }
}
