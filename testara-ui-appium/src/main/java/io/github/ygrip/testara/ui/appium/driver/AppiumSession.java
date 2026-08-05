package io.github.ygrip.testara.ui.appium.driver;

import java.util.Map;
import java.util.Optional;

import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.SessionId;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.appium.capability.AppiumAssertionCapability;
import io.github.ygrip.testara.ui.appium.capability.AppiumInteractionCapability;
import io.github.ygrip.testara.ui.appium.capability.AppiumNavigationCapability;
import io.github.ygrip.testara.ui.appium.capability.AppiumObservationCapability;
import io.github.ygrip.testara.ui.appium.capability.AppiumWaitCapability;
import io.github.ygrip.testara.ui.appium.config.AppiumDriverProperties;
import io.github.ygrip.testara.ui.appium.page.AppiumPageFinder;
import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.driver.CurrentPageHolder;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;

import io.appium.java_client.AppiumDriver;
import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("unchecked")
public final class AppiumSession implements DriverSession<AppiumDriver> {
  private final DeviceType deviceType;
  private AppiumDriver driver;
  private final CurrentPageHolder pageState = new CurrentPageHolder(this);

  public AppiumSession() {
    this.deviceType = DeviceType.ANDROID;
  }

  @Override
  public PageContext<?> currentPage() {
    return pageState.current();
  }

  @Override
  public void activatePage(PageContext<?> page) {
    pageState.activate(page);
  }

  @Override
  public void clearCurrentPage() {
    pageState.clear();
  }

  @Override
  public AppiumDriver instance() {
    return driver;
  }

  @Override
  public DeviceType platform() {
    return Optional.of(deviceType)
      .orElse(DeviceType.ANDROID);
  }

  @Override
  public DriverSession<AppiumDriver> using(AppiumDriver driver) {
    this.driver = driver;
    return this;
  }

  @Override
  public DriverSession<AppiumDriver> on(DeviceType platform) {
    log.warn("Action not supported");
    return this;
  }

  @Override
  public <T> T capability(Class<T> capabilityType) {
    if (driver == null) {
      throw new IllegalStateException("Session not initialized: no driver bound");
    }
    // Capability implementations can be added here; unsupported types fail fast (plan §4.2)
    if (capabilityType == NavigationCapability.class) {
      return capabilityType.cast(new AppiumNavigationCapability(driver));
    }
    if (capabilityType == InteractionCapability.class) {
      return capabilityType.cast(new AppiumInteractionCapability(driver));
    }
    if (capabilityType == AssertionCapability.class) {
      return capabilityType.cast(new AppiumAssertionCapability(driver));
    }
    if (capabilityType == WaitCapability.class) {
      return capabilityType.cast(new AppiumWaitCapability(driver));
    }
    if (capabilityType == ObservationCapability.class) {
      return capabilityType.cast(new AppiumObservationCapability(driver));
    }
    throw new UnsupportedOperationException("Capability not supported: " + capabilityType.getName());
  }

  @Override
  public Class<AppiumDriverProperties> configType() {
    return AppiumDriverProperties.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public AppiumPageFinder finder() {
    AppiumPageFinder finder;
    try {
      finder = TestFramework.context()
        .get(AppiumPageFinder.class);
    } catch (Exception ignored) {
      finder = TestFramework.factory()
        .getInstance(AppiumPageFinder.class);
    }
    finder.setDeviceType(platform());
    finder.bindSession(this);
    return finder;
  }

  @Override
  public void close() {
    if (driver != null) {
      log.info("#Quit driver on session with name : {}", sessionName());
      try {
        driver.quit();
      } finally {
        driver = null;
        pageState.clear();
      }
    }
  }

  @Override
  public boolean isActive() {
    if (driver == null) {
      return false;
    }
    if (driver instanceof RemoteWebDriver remoteWebDriver) {
      try {
        SessionId sessionId = remoteWebDriver.getSessionId();
        CommandPayload payload = new CommandPayload(DriverCommand.GET_WINDOW_HANDLES, Map.of());
        Response response = remoteWebDriver.getCommandExecutor()
          .execute(new Command(sessionId, payload));
        if (response == null) {
          return false;
        } else {
          Object value = response.getValue();
          if (value == null) {
            return false;
          } else if (value instanceof NoSuchSessionException) {
            return false;
          } else {
            return response.getState()
              .equalsIgnoreCase("success");
          }
        }
      } catch (Exception ignored) {
        return false;
      }
    }
    return true;
  }
}
