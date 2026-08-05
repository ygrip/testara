package io.github.ygrip.testara.ui.executor;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.DriverInstances;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the fix that lets {@code ExecutorFactory.withPropagatedContext} carry
 * the current driver/actor session onto a fresh worker thread (e.g. ValidatorHelper's
 * per-validation virtual threads) - without this, {@code ActorManager.currentActor()} crashed
 * with "No current driver session" because that worker thread never had one to begin with.
 */
class UiThreadContextPropagatorTest {

  static final class FakeSession implements DriverSession<Object> {
    @Override public <T> T capability(Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public Class<? extends AbstractDriverProperties> configType() { return null; }
    @Override public <F extends PageFinder<?, ?, ?>> F finder() { return null; }
    @Override public void close() { }
    @Override public boolean isActive() { return true; }
    @Override public Object instance() { return null; }
    @Override public DeviceType platform() { return DeviceType.DEFAULT; }
    @Override public DriverSession<Object> using(Object driver) { return this; }
    @Override public DriverSession<Object> on(DeviceType platform) { return this; }
    @Override public PageContext<?> currentPage() { return null; }
    @Override public void activatePage(PageContext<?> page) { }
    @Override public void clearCurrentPage() { }
  }

  @AfterEach
  void clearThreadLocals() {
    DriverSessionManager.bindToCurrentThread(null);
    ActorManager.bindToCurrentThread(null);
  }

  @Test
  void capturedSessionAndActorsAreVisibleOnAFreshThreadAndUnboundAfter() throws InterruptedException {
    FakeSession session = new FakeSession();
    DriverInstances instances = DriverSessionManager.inThisTestThread();
    instances.registerDriver("chrome").forDriver(session);
    instances.setCurrentActiveDriver(session);
    ActorManager.currentActor();

    UiThreadContextPropagator propagator = new UiThreadContextPropagator();
    Object snapshot = propagator.capture();

    AtomicReference<DriverSession<?>> seenDriver = new AtomicReference<>();
    AtomicReference<DriverInstances> seenInstancesAfter = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);

    Thread worker = new Thread(() -> {
      propagator.bind(snapshot);
      seenDriver.set(DriverSessionManager.inThisTestThread().getCurrentDriver());
      propagator.unbind();
      seenInstancesAfter.set(DriverSessionManager.getInstances());
      done.countDown();
    });
    worker.start();

    assertTrue(done.await(5, TimeUnit.SECONDS), "worker thread must complete");
    assertSame(session, seenDriver.get(),
        "a fresh thread must see the caller's driver session once the snapshot is bound");
    assertNull(seenInstancesAfter.get(),
        "unbind() must remove the driver instances so they don't leak into whatever runs next on that thread");
  }
}
