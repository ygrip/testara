package io.github.ygrip.testara.ui.executor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;

public final class ActorManager {
  private static final ThreadLocal<Map<String, Actor>> ACTORS = new InheritableThreadLocal<>() {
    @Override
    protected Map<String, Actor> childValue(Map<String, Actor> parentValue) {
      // Copy-on-inherit: a child thread must not mutate the same map instance as its parent.
      return parentValue == null ? null : new ConcurrentHashMap<>(parentValue);
    }
  };

  private ActorManager() {

  }

  public static Actor currentActor() {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    return actorWith(session);
  }

  public static Actor actorWith(DriverSession<?> session) {
    Map<String, Actor> currentActors = Optional.ofNullable(ACTORS.get())
      .orElse(new ConcurrentHashMap<>());
    final var key = actorKey(session);
    currentActors.computeIfAbsent(key, unused -> Actor.with(session));
    ACTORS.set(currentActors);

    return currentActors.get(key);
  }

  public static Map<String, Actor> getActors() {
    return ACTORS.get();
  }

  public static void bindToCurrentThread(Map<String, Actor> actors) {
    if (actors == null) {
      ACTORS.remove();
      return;
    }
    ACTORS.set(new ConcurrentHashMap<>(actors));
  }

  /**
   * ConcurrentHashMap disallows null keys, and a null sessionName would otherwise collapse every
   * unnamed session onto a single actor - key unnamed sessions by their unique driver identity instead.
   */
  private static String actorKey(DriverSession<?> session) {
    final var sessionName = session.sessionName();
    return sessionName != null ? sessionName : "__unnamed__:" + System.identityHashCode(session);
  }
}
