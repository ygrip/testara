package io.github.ygrip.testara.ui.selenium.config;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.openqa.selenium.PageLoadStrategy;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.EmulationModel;
import io.github.ygrip.testara.ui.model.RemoteDriverConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@LoadProperties(prefix = "selenium.driver")
public class SeleniumDriverProperties extends AbstractDriverProperties {
  private boolean enableWebSocket = true;
  private boolean maximizeBrowser = true;
  private boolean clearCache = false;
  private String pageLoadStrategy = PageLoadStrategy.NORMAL.toString();
  private Map<DeviceType, Map<String, String>> userAgent = new HashMap<>();
  private Map<DeviceType, Map<String, String>> binaryPath = new HashMap<>();
  private Map<DeviceType, Map<String, Map<String, Object>>> capabilities = new HashMap<>();
  private Map<DeviceType, Map<String, List<String>>> args = new HashMap<>();
  private Map<String, String> version = new HashMap<>();
  private Map<DeviceType, RemoteDriverConfig> remoteDriver = new HashMap<>();
  private Map<DeviceType, Map<String, EmulationModel>> emulation = new HashMap<>();

  public PageLoadStrategy resolvePageLoadStrategy() {
    String configured = Optional.ofNullable(pageLoadStrategy)
      .map(String::trim)
      .filter(value -> !value.isEmpty())
      .orElse(PageLoadStrategy.NORMAL.toString())
      .toLowerCase(Locale.ROOT);

    return switch (configured) {
      case "normal" -> PageLoadStrategy.NORMAL;
      case "eager" -> PageLoadStrategy.EAGER;
      case "none" -> PageLoadStrategy.NONE;
      default -> throw new IllegalArgumentException(
        "Unsupported Selenium page load strategy '%s'. Supported values: normal, eager, none"
          .formatted(pageLoadStrategy)
      );
    };
  }
}
