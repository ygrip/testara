package io.github.ygrip.testara.ui.executor;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-hti.5: {@code ActorManager} used a plain
 * {@code InheritableThreadLocal<Map>} with no {@code childValue()} override, so a child thread
 * shared the exact same mutable map instance as its parent - concurrent {@code actorWith} calls
 * from parent and child raced on the same {@code HashMap}. A null {@code sessionName()} also
 * collapsed every unnamed session onto a single actor.
 */
class ActorManagerTest {

  static final class FakeSession implements DriverSession<Object> {
    private final String name;

    FakeSession(String name) { this.name = name; }

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
    @Override public String sessionName() { return name; }
  }

  @AfterEach
  void clearThreadLocal() {
    ActorManager.bindToCurrentThread(null);
    DriverSessionManager.bindToCurrentThread(null);
  }

  /**
   * Regression coverage: currentActor() used to hand a null session straight to
   * actorWith()/Actor.with(), which failed three frames down inside
   * SessionInteractionContext.from() with an opaque "session cannot be null" - and callers like
   * ElementVisibilityValidation.validate() swallow that as a false validation result instead of a
   * clear setup error. Fail fast here instead, matching Actor.withCurrentSession()'s message.
   */
  @Test
  void currentActorFailsFastWithAClearMessageWhenNoDriverIsRegistered() {
    DriverSessionManager.bindToCurrentThread(null);

    IllegalStateException ex = assertThrows(IllegalStateException.class, ActorManager::currentActor);
    assertEquals("No current driver session. Register a driver with DriverSessionManager first.", ex.getMessage());
  }

  @Test
  void reusesTheSameActorForTheSameNamedSession() {
    FakeSession session = new FakeSession("chrome");

    Actor first = ActorManager.actorWith(session);
    Actor second = ActorManager.actorWith(session);

    assertSame(first, second, "repeated calls for the same session name must return the same actor");
  }

  @Test
  void unnamedSessionsDoNotCollapseIntoASingleActor() {
    FakeSession sessionA = new FakeSession(null);
    FakeSession sessionB = new FakeSession(null);

    Actor actorA = ActorManager.actorWith(sessionA);
    Actor actorB = ActorManager.actorWith(sessionB);

    assertNotSame(actorA, actorB, "two distinct unnamed sessions must not collapse onto one actor");
    assertEquals(2, ActorManager.getActors().size());
  }

  @Test
  void childThreadInheritsACopyNotTheSharedMapInstance() throws InterruptedException {
    ActorManager.actorWith(new FakeSession("parent-session"));
    Map<String, Actor> parentActors = ActorManager.getActors();

    AtomicReference<Map<String, Actor>> childActors = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    Thread child = new Thread(() -> {
      ActorManager.actorWith(new FakeSession("child-session"));
      childActors.set(ActorManager.getActors());
      done.countDown();
    });
    child.start();
    assertTrue(done.await(5, TimeUnit.SECONDS), "child thread must complete");
    child.join();

    assertNotSame(parentActors, childActors.get(),
        "child thread must inherit a copy, not the same map reference, so it can't race the parent");
    assertTrue(parentActors.containsKey("parent-session"));
    assertTrue(!parentActors.containsKey("child-session"),
        "mutating the inherited copy on the child thread must not leak back into the parent's map");
  }
}
