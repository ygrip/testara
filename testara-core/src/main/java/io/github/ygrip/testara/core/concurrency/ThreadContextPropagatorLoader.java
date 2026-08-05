package io.github.ygrip.testara.core.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class ThreadContextPropagatorLoader {
  private ThreadContextPropagatorLoader() {

  }

  public static List<ThreadContextPropagator> load() {
    List<ThreadContextPropagator> propagators = new ArrayList<>();
    ServiceLoader.load(ThreadContextPropagator.class, Thread.currentThread().getContextClassLoader())
      .forEach(propagators::add);
    return propagators;
  }
}
