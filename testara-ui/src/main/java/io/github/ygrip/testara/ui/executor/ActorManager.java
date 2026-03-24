package io.github.ygrip.testara.ui.executor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;

public final class ActorManager {
  private static final ThreadLocal<Map<String, Actor>> ACTORS = new InheritableThreadLocal<>();

  private ActorManager() {

  }

  public static Actor currentActor() {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    return actorWith(session);
  }

  public static Actor actorWith(DriverSession<?> session) {
    Map<String, Actor> currentActors = Optional.ofNullable(ACTORS.get())
      .orElse(new HashMap<>());
    final var sessionName = session.sessionName();
    if (!currentActors.containsKey(sessionName)) {
      currentActors.put(sessionName, Actor.with(session));
    }
    ACTORS.set(currentActors);

    return currentActors.get(sessionName);
  }

  public static Map<String, Actor> getActors() {
    return ACTORS.get();
  }

  public static void bindToCurrentThread(Map<String, Actor> actors) {
    if (actors == null) {
      ACTORS.remove();
      return;
    }
    ACTORS.set(new HashMap<>(actors));
  }
}
