package io.github.ygrip.testara.ui.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.config.EngineProperties;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.DriverResetMode;

/**
 * Applies {@link EngineProperties#getDriverResetMode()} on top of the default worker-thread-scoped
 * driver lifecycle ({@link DriverSessionManager}, which otherwise never quits a driver until the
 * whole run ends):
 * <ul>
 *   <li>{@code NEVER} - no-op here; the run-end shutdown in {@code DriverSessionManager} handles it.</li>
 *   <li>{@code ON_EACH_SCENARIO} - quit after every scenario, same as the framework's historical
 *       default.</li>
 *   <li>{@code ON_EACH_SUITE} - quit only when this worker thread's next scenario belongs to a
 *       different feature file than its last one.</li>
 * </ul>
 */
public class DriverResetHooks {

  private static final ThreadLocal<String> LAST_FEATURE_URI = new ThreadLocal<>();

  private DriverResetMode mode() {
    return TestFramework.configuration()
      .get(EngineProperties.class)
      .getDriverResetMode();
  }

  @Before(order = 100)
  public void beforeScenario(Scenario scenario) {
    if (mode() != DriverResetMode.ON_EACH_SUITE) {
      return;
    }
    String uri = scenario.getUri().toString();
    String lastUri = LAST_FEATURE_URI.get();
    if (lastUri != null && !lastUri.equals(uri)) {
      DriverSessionManager.tearDown();
    }
    LAST_FEATURE_URI.set(uri);
  }

  @After(order = 100000)
  public void afterScenario() {
    if (mode() == DriverResetMode.ON_EACH_SCENARIO) {
      DriverSessionManager.tearDown();
    }
  }

}
