package io.github.ygrip.testara.ui.selenium.driver;

import java.util.Map;
import java.util.Optional;

import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.SessionId;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.selenium.capability.SeleniumAssertionCapability;
import io.github.ygrip.testara.ui.selenium.capability.SeleniumInteractionCapability;
import io.github.ygrip.testara.ui.selenium.capability.SeleniumNavigationCapability;
import io.github.ygrip.testara.ui.selenium.capability.SeleniumObservationCapability;
import io.github.ygrip.testara.ui.selenium.capability.SeleniumWaitCapability;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.selenium.config.SeleniumDriverProperties;
import io.github.ygrip.testara.ui.selenium.page.SeleniumPageFinder;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class SeleniumSession implements DriverSession<WebDriver> {
  private WebDriver driver;
  private DeviceType deviceType;

  @Override
  public WebDriver instance() {
    return driver;
  }

  @Override
  public DeviceType platform() {
    return Optional.ofNullable(deviceType).orElse(DeviceType.DEFAULT);
  }

  @Override
  public DriverSession<WebDriver> using(WebDriver driver) {
    this.driver = driver;
    return this;
  }

  @Override
  public DriverSession<WebDriver> on(DeviceType platform) {
    this.deviceType = platform;
    return this;
  }

  @Override
  public <T> T capability(Class<T> capabilityType) {
    if (driver == null) {
      throw new IllegalStateException("Session not initialized: no driver bound");
    }
    // Capability implementations can be added here; unsupported types fail fast (plan §4.2)
    if (capabilityType == NavigationCapability.class) {
      return capabilityType.cast(new SeleniumNavigationCapability(driver));
    }
    if (capabilityType == InteractionCapability.class) {
      return capabilityType.cast(new SeleniumInteractionCapability(driver));
    }
    if (capabilityType == AssertionCapability.class) {
      return capabilityType.cast(new SeleniumAssertionCapability(driver));
    }
    if (capabilityType == WaitCapability.class) {
      return capabilityType.cast(new SeleniumWaitCapability(driver));
    }
    if (capabilityType == ObservationCapability.class) {
      return capabilityType.cast(new SeleniumObservationCapability(driver));
    }
    throw new UnsupportedOperationException("Capability not supported: " + capabilityType.getName());
  }

  @Override
  public Class<SeleniumDriverProperties> configType() {
    return SeleniumDriverProperties.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SeleniumPageFinder finder() {
    SeleniumPageFinder finder;
    try {
      finder = TestFramework.context()
        .get(SeleniumPageFinder.class);
    } catch (Exception ignored) {
      finder = TestFramework.factory()
        .getInstance(SeleniumPageFinder.class);
    }
    finder.setDeviceType(platform());
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
