package io.github.ygrip.testara.ui.vibium.config;

import java.util.HashMap;
import java.util.Map;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.model.DeviceType;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Vibium-specific driver configuration. Owner, headless, scan-locations, containerized and the
 * {@code binaryPath} accessor used at launch time live on
 * {@link AbstractDriverProperties}/{@link io.github.ygrip.testara.ui.driver.AbstractDriver} — only
 * Vibium-specific fields are declared here.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@LoadProperties(prefix = "vibium.browser")
public class VibiumDriverProperties extends AbstractDriverProperties {
  /**
   * Path to the Vibium executable (not the Chrome executable). Maps to
   * {@code vibium.browser.vibium-binary-path} and {@code StartOptions.executablePath(...)}.
   */
  private String vibiumBinaryPath;

  /**
   * Remote-connect endpoint, distinct from the inherited {@code boolean remote} flag. Maps to
   * {@code StartOptions.connectURL(...)}/{@code connectHeaders(...)}.
   */
  private VibiumRemoteConfig remoteConnect = new VibiumRemoteConfig();

  /**
   * Per-device viewport profiles. Declared and bindable only in Phase 1 — not yet applied to a
   * live session; Phase 2/3 wires this into {@code Page.setViewport(ViewportSize)}.
   */
  private Map<DeviceType, VibiumViewportSize> viewport = new HashMap<>();
}
