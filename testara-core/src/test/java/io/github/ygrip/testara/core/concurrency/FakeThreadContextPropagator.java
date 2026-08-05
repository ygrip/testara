package io.github.ygrip.testara.core.concurrency;

/**
 * Test double registered via {@code META-INF/services} so {@link ThreadContextPropagatorLoader}
 * (and therefore {@link ExecutorFactory#withPropagatedContext}) picks it up like a real
 * module-provided propagator would.
 */
public class FakeThreadContextPropagator implements ThreadContextPropagator {

  static final ThreadLocal<String> VALUE = new ThreadLocal<>();

  @Override
  public Object capture() {
    return VALUE.get();
  }

  @Override
  public void bind(Object snapshot) {
    VALUE.set((String) snapshot);
  }

  @Override
  public void unbind() {
    VALUE.remove();
  }
}
