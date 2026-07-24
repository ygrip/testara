package io.github.ygrip.testara.ui.executor;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Engine-agnostic user action facade. Uses {@link DriverSessionManager} to obtain the current
 * {@link DriverSession} and delegates to capabilities (navigation, interaction, etc.). No direct
 * dependency on Selenium, Appium, or other UI libraries; interacts with them via capabilities.
 */
public abstract class UserAction {

  /**
   * Current session from driver manager (same thread).
   */
  protected static DriverSession<?> currentSession() {
    return DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
  }

  /**
   * Resolve capability from current session; throws if no session or unsupported.
   */
  protected static <T> T capability(Class<T> type) {
    DriverSession<?> session = currentSession();
    if (session == null) {
      throw new IllegalStateException("No current driver session. Register a driver with DriverSessionManager first.");
    }
    return session.capability(type);
  }

  public String getCurrentUrl() {
    return capability(NavigationCapability.class).getCurrentUrl();
  }

  public DeviceType getDeviceType() {
    return DeviceType.DEFAULT;
  }

  /**
   * Execute script via current driver (uses reflection so no engine type in signature).
   */
  public Object executeScript(String command, Object... variable) {
    return capability(InteractionCapability.class).executeScript(command, variable);
  }

  public Object injectScript(String fileName) {
    return null;
  }

  public void reload() {
    capability(NavigationCapability.class).refresh();
  }

  public void refresh() {
    capability(NavigationCapability.class).refresh();
  }

  public void close() {
    DriverSession<?> session = currentSession();
    if (session != null) {
      session.close();
    }
  }

  public void back() {
    capability(NavigationCapability.class).back();
  }

  public void forward() {
    capability(NavigationCapability.class).forward();
  }

  /**
   * Raw driver instance (e.g. WebDriver). Engine-specific; use for advanced or legacy use only.
   * Prefer capabilities for agnostic behaviour.
   */
  public Object getDriver() {
    DriverSession<?> session = currentSession();
    return Optional.ofNullable(session)
      .map(DriverSession::instance)
      .orElse(null);
  }

  public <T> T driverOf(Class<T> type) {
    DriverSession<?> session = currentSession();
    return Optional.ofNullable(session)
      .map(driver -> session.instanceOf(type))
      .orElse(null);
  }

  public void open(String page) {
    capability(NavigationCapability.class).to(page);
  }

  public void doAction(String action) throws Exception {
    ActionResolver.doActionOnPage(action, null, null);
  }

  public void doAction(String action, Map<String, Object> additionalParameter) throws Exception {
    ActionResolver.doActionOnPage(action, null, additionalParameter);
  }

  public void doAction(String action, String page) throws Exception {
    ActionResolver.doActionOnPage(action, null, Map.of());
  }

  public boolean isIn(String page) {
    try {
      String url = getCurrentUrl();
      return url != null && url.contains(page);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Scroll to element; uses reflection to call scrollIntoView on the resolved element if supported.
   */
  public void scrollTo(Locator element) {
    capability(InteractionCapability.class).scrollTo(Element.of(element).build(), true);
  }

  /**
   * Resolve element by locator string (e.g. "css:#id", "id:foo"). Engine-specific handle.
   */
  public Object findElement(String element) {
    return capability(InteractionCapability.class).findElement(Element.of(element).build());
  }

  public List<?> findElements(String element) {
    List<?> list = capability(InteractionCapability.class).findElements(Element.of(element).build());
    return list != null ? list : Collections.emptyList();
  }

  public <T> T getPage(Class<T> page) {
    try {
      return TestFramework.context()
        .get(page);
    } catch (Exception e) {
      return null;
    }
  }

  public void await(long timeMillis) {
    if (timeMillis > 0) {
      Awaitility.await()
        .pollInSameThread()
        .pollDelay(timeMillis, TimeUnit.MILLISECONDS)
        .atMost(timeMillis + 1, TimeUnit.MILLISECONDS)
        .until(() -> true);
    }
  }

  public void await(Duration duration) {
    if (duration.isPositive()) {
      Awaitility.await()
        .pollInSameThread()
        .pollDelay(duration)
        .atMost(duration.plusMillis(1))
        .until(() -> true);
    }
  }

  public void clearText(String element) {
    capability(InteractionCapability.class).clear(Element.of(element).build());
  }

  /**
   * Execute Screenplay-style interactions against the current session (plan §5).
   * Example: {@code attemptsTo(Navigate.to("/login"), Enter.text("admin").into("#username"), Click.on("#submit")); }
   */
  public void attemptsTo(Interaction... interactions) {
    actor().attemptsTo(interactions);
  }

  public Actor actor() {
    return ActorManager.currentActor();
  }
}
