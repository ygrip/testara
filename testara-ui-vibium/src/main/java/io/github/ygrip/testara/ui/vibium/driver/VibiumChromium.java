package io.github.ygrip.testara.ui.vibium.driver;

import org.apache.commons.lang3.StringUtils;

import com.vibium.Browser;
import com.vibium.Vibium;
import com.vibium.types.StartOptions;

import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.vibium.engine.VibiumEngine;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;

/**
 * Vibium only ever launches Chromium (no Firefox/WebKit parity), so unlike Playwright's
 * {@code Chromium}/{@code PlaywrightFirefox}/{@code Webkit} split, this driver implements
 * {@link #create(StartOptions)} directly instead of delegating browser-family selection to the
 * engine.
 */
@DriverMetadata(name = "chrome",
  browserName = "chromium",
  engine = VibiumEngine.class,
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class VibiumChromium extends AbstractDriver<Browser, StartOptions> {

  @Override
  public Browser create(StartOptions options) {
    return Vibium.start(options);
  }

  @Override
  public StartOptions proxyOptions() {
    if (getProxyType() != null) {
      throw new UnsupportedVibiumCapabilityException(
        "proxy",
        "StartOptions (Vibium 26.5.31) has no HTTP/SOCKS proxy or arbitrary launch-argument option; "
          + "use remote connect (StartOptions.connectURL) to attach to an externally-configured "
          + "browser instead of routing local Chromium traffic through a proxy"
      );
    }
    return defaultOptions();
  }

  @Override
  public StartOptions mobileOptions() {
    // StartOptions has no mobile-specific launch field; the mobile-web viewport/media/touch
    // profile is applied at the Page/session level once VibiumSession owns a page (Phase 2/3).
    return defaultOptions();
  }

  @Override
  public StartOptions defaultOptions() {
    StartOptions options = new StartOptions().headless(isHeadless());

    final var binaryPath = getBinaryPath();
    if (StringUtils.isNotBlank(binaryPath)) {
      options = options.executablePath(binaryPath);
    }

    return options;
  }

  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
