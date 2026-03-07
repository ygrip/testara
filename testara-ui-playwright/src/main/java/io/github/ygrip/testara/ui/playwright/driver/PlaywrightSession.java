package io.github.ygrip.testara.ui.playwright.driver;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightAssertionCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightInteractionCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightNavigationCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightObservationCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightWaitCapability;
import io.github.ygrip.testara.ui.playwright.config.PlaywrightDriverProperties;
import io.github.ygrip.testara.ui.playwright.page.PlaywrightPageFinder;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class PlaywrightSession implements DriverSession<Browser> {
  private Browser driver;
  private DeviceType deviceType;
  private BrowserContext browserContext;
  private Page page;
  private String userAgent;
  private String stealthInitScript;
  private Integer viewportWidth;
  private Integer viewportHeight;
  private Double deviceScaleFactor;
  private Boolean isMobile;
  private Boolean hasTouch;

  @Override
  public Browser instance() {
    return driver;
  }

  /**
   * Called by the engine to inject browser-specific stealth configuration
   * resolved from properties and the driver's {@link StealthProvider}.
   */
  public void withStealthConfig(String userAgent, String stealthInitScript) {
    this.userAgent = userAgent;
    this.stealthInitScript = stealthInitScript;
  }

  /**
   * Called by the engine to set viewport and device emulation parameters
   * applied when the {@link BrowserContext} is created.
   */
  public void withViewportConfig(int width, int height, Double deviceScaleFactor,
    Boolean isMobile, Boolean hasTouch) {
    this.viewportWidth = width;
    this.viewportHeight = height;
    this.deviceScaleFactor = deviceScaleFactor;
    this.isMobile = isMobile;
    this.hasTouch = hasTouch;
  }

  public Page page() {
    if (page == null || page.isClosed()) {
      if (browserContext == null) {
        browserContext = createContext();
      }
      page = browserContext.newPage();
      if (StringUtils.isNotBlank(stealthInitScript)) {
        page.addInitScript(stealthInitScript);
      }
    }
    return page;
  }

  /**
   * Replace the active page reference. Called by the navigation capability
   * when switching or opening tabs so all subsequent capability access
   * targets the correct page.
   */
  public void setActivePage(Page newPage) {
    this.page = newPage;
  }

  public BrowserContext context() {
    if (browserContext == null) {
      browserContext = createContext();
    }
    return browserContext;
  }

  private BrowserContext createContext() {
    int width = Optional.ofNullable(viewportWidth).orElse(1280);
    int height = Optional.ofNullable(viewportHeight).orElse(720);

    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
      .setViewportSize(width, height)
      .setLocale("en-US")
      .setTimezoneId("America/New_York");

    if (StringUtils.isNotBlank(userAgent)) {
      contextOptions.setUserAgent(userAgent);
    }
    if (deviceScaleFactor != null && deviceScaleFactor > 0) {
      contextOptions.setDeviceScaleFactor(deviceScaleFactor);
    }
    if (Boolean.TRUE.equals(isMobile)) {
      contextOptions.setIsMobile(true);
    }
    if (Boolean.TRUE.equals(hasTouch)) {
      contextOptions.setHasTouch(true);
    }

    log.debug("Creating BrowserContext with viewport {}x{}, mobile={}, touch={}, scaleFactor={}",
      width, height, isMobile, hasTouch, deviceScaleFactor);

    return driver.newContext(contextOptions);
  }

  @Override
  public DeviceType platform() {
    return Optional.ofNullable(deviceType).orElse(DeviceType.DEFAULT);
  }

  @Override
  public DriverSession<Browser> using(Browser driver) {
    this.driver = driver;
    return this;
  }

  @Override
  public DriverSession<Browser> on(DeviceType platform) {
    this.deviceType = platform;
    return this;
  }

  @Override
  public <T> T capability(Class<T> capabilityType) {
    if (driver == null) {
      throw new IllegalStateException("Session not initialized: no driver bound");
    }
    Page currentPage = page();
    if (capabilityType == NavigationCapability.class) {
      return capabilityType.cast(new PlaywrightNavigationCapability(this));
    }
    if (capabilityType == InteractionCapability.class) {
      return capabilityType.cast(new PlaywrightInteractionCapability(currentPage));
    }
    if (capabilityType == AssertionCapability.class) {
      return capabilityType.cast(new PlaywrightAssertionCapability(currentPage));
    }
    if (capabilityType == WaitCapability.class) {
      return capabilityType.cast(new PlaywrightWaitCapability(currentPage));
    }
    if (capabilityType == ObservationCapability.class) {
      return capabilityType.cast(new PlaywrightObservationCapability(currentPage));
    }
    throw new UnsupportedOperationException("Capability not supported: " + capabilityType.getName());
  }

  @Override
  public Class<PlaywrightDriverProperties> configType() {
    return PlaywrightDriverProperties.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlaywrightPageFinder finder() {
    PlaywrightPageFinder finder;
    try {
      finder = TestFramework.context()
        .get(PlaywrightPageFinder.class);
    } catch (Exception ignored) {
      finder = TestFramework.factory()
        .getInstance(PlaywrightPageFinder.class);
    }
    finder.setDeviceType(platform());
    return finder;
  }

  @Override
  public void close() {
    if (driver != null) {
      log.info("#Quit driver on session with name : {}", sessionName());
      try {
        if (page != null && !page.isClosed()) {
          page.close();
        }
        if (browserContext != null) {
          browserContext.close();
        }
        driver.close();
      } finally {
        page = null;
        browserContext = null;
        driver = null;
      }
    }
  }

  @Override
  public boolean isActive() {
    if (driver == null) {
      return false;
    }
    return driver.isConnected();
  }
}
