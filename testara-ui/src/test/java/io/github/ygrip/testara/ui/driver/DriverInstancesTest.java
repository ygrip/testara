package io.github.ygrip.testara.ui.driver;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-hti.3: {@code DriverSessionManager.tearDown()} had no caller and
 * {@code driversUsedInCurrentThread} was never populated on registration, so every scenario leaked
 * its browser/WebDriver process. This covers the {@code DriverInstances} half of the fix: session
 * registration must populate {@code driversUsedInCurrentThread}, and {@code closeAllDrivers()} must
 * actually invoke {@code close()} on every registered session.
 */
class DriverInstancesTest {

  static final class FakeSession implements DriverSession<Object> {
    private boolean closed = false;

    @Override public <T> T capability(Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public Class<? extends AbstractDriverProperties> configType() { return null; }
    @Override public <F extends PageFinder<?, ?, ?>> F finder() { return null; }
    @Override public void close() { closed = true; }
    @Override public boolean isActive() { return !closed; }
    @Override public Object instance() { return null; }
    @Override public DeviceType platform() { return DeviceType.DEFAULT; }
    @Override public DriverSession<Object> using(Object driver) { return this; }
    @Override public DriverSession<Object> on(DeviceType platform) { return this; }
    @Override public PageContext<?> currentPage() { return null; }
    @Override public void activatePage(PageContext<?> page) { }
    @Override public void clearCurrentPage() { }

    boolean isClosed() { return closed; }
  }

  @Test
  void registeringADriverTracksItInCurrentThread() {
    DriverInstances instances = new DriverInstances();
    FakeSession session = new FakeSession();

    instances.registerDriver("chrome").forDriver(session);

    assertTrue(instances.getActiveDriverMap().containsKey("chrome"));
  }

  @Test
  void closeAllDriversQuitsEveryRegisteredSession() {
    DriverInstances instances = new DriverInstances();
    FakeSession chrome = new FakeSession();
    FakeSession firefox = new FakeSession();
    instances.registerDriver("chrome").forDriver(chrome);
    instances.registerDriver("firefox").forDriver(firefox);

    instances.closeAllDrivers();

    assertTrue(chrome.isClosed(), "closeAllDrivers must quit every registered session, not just the active one");
    assertTrue(firefox.isClosed(), "closeAllDrivers must quit every registered session, not just the active one");
    assertFalse(instances.getActiveDriverMap().containsKey("chrome"));
  }

  @Test
  void tearDownRemovesTheInstancesFromTheCurrentThread() {
    DriverInstances instances = new DriverInstances();
    FakeSession session = new FakeSession();
    instances.registerDriver("chrome").forDriver(session);
    DriverSessionManager.bindToCurrentThread(instances);

    DriverSessionManager.tearDown();

    assertTrue(session.isClosed(), "DriverSessionManager.tearDown() must quit the driver, not just detach it");
    assertTrue(DriverSessionManager.inThisTestThread() != instances,
        "tearDown() must remove the DriverInstances from the thread so the next scenario starts fresh");
  }
}
