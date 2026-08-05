package io.github.ygrip.testara.ui.config;

import java.util.HashSet;
import java.util.Set;

import io.github.ygrip.testara.core.config.LoadProperties;

import io.github.ygrip.testara.ui.model.DriverResetMode;
import io.github.ygrip.testara.ui.model.ScreenshotOutputType;
import io.github.ygrip.testara.ui.model.ScreenshotStrategy;
import lombok.Data;

@Data
@LoadProperties(prefix = "automation.engine")
public class EngineProperties {
  private String defaultEngine;
  private Set<String> activeEngines = new HashSet<>();
  private ScreenshotStrategy screenshotStrategy = ScreenshotStrategy.NONE;
  private ScreenshotOutputType screenshotOutputType = ScreenshotOutputType.IMAGE;
  private int screenshotFps = 30;
  private boolean forceResolution = true;
  private int videoBitRate = 4;
  private DriverResetMode driverResetMode = DriverResetMode.NEVER;
}
