package io.github.ygrip.testara.ui.executor;

import io.github.ygrip.testara.core.concurrency.ThreadContextPropagator;
import io.github.ygrip.testara.ui.driver.DriverInstances;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;

import java.util.Map;

/**
 * Propagates the calling thread's {@link DriverSessionManager} instances and {@link ActorManager}
 * actors onto whichever thread an executor created via {@code ExecutorFactory} actually runs a
 * task on - e.g. {@code ValidatorHelper}'s per-validation virtual threads, which otherwise never
 * see the current driver session, since neither {@code ThreadLocal} is inheritable across
 * unrelated threads.
 */
public class UiThreadContextPropagator implements ThreadContextPropagator {

  private record Snapshot(DriverInstances instances, Map<String, Actor> actors) {
  }

  @Override
  public Object capture() {
    return new Snapshot(DriverSessionManager.getInstances(), ActorManager.getActors());
  }

  @Override
  public void bind(Object snapshot) {
    Snapshot s = (Snapshot) snapshot;
    DriverSessionManager.bindToCurrentThread(s.instances());
    ActorManager.bindToCurrentThread(s.actors());
  }

  @Override
  public void unbind() {
    DriverSessionManager.bindToCurrentThread(null);
    ActorManager.bindToCurrentThread(null);
  }
}
