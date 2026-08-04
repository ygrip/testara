package io.github.ygrip.testara.ui.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;

import lombok.extern.log4j.Log4j2;

/**
 * Quits every WebDriver/browser session opened by the scenario. Runs after
 * {@link ScreenshotHooks}'s screenshot/video capture (order high enough to run last among
 * scenario-scoped {@code @After} hooks) so the driver is still alive while evidence is attached.
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class DriverTeardownHooks {

  @After(order = 100000)
  public void tearDownDrivers(Scenario scenario) {
    try {
      DriverSessionManager.tearDown();
    } catch (Exception e) {
      log.warn("Failed to tear down driver session(s) for scenario '{}': {}", scenario.getName(), e.getMessage());
    }
  }
}
