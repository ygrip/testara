package io.github.ygrip.testara.ui.playwright.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.EmulationModel;
import io.github.ygrip.testara.ui.model.RemoteDriverConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@LoadProperties(prefix = "playwright.browser")
public class PlaywrightDriverProperties extends AbstractDriverProperties {
  private boolean enableWebSocket = true;
  private boolean maximizeBrowser = true;
  private boolean clearCache = false;
  private Map<DeviceType, Map<String, String>> userAgent = new HashMap<>();
  private Map<DeviceType, Map<String, String>> binaryPath = new HashMap<>();
  private Map<DeviceType, Map<String, Map<String, Object>>> capabilities = new HashMap<>();
  private Map<DeviceType, Map<String, List<String>>> args = new HashMap<>();
  private Map<String, String> version = new HashMap<>();
  private Map<DeviceType, RemoteDriverConfig> remoteDriver = new HashMap<>();
  private Map<DeviceType, Map<String, EmulationModel>> emulation = new HashMap<>();
}
