package io.github.ygrip.testara.core.concurrency;

/**
 * SPI for propagating thread-confined test state (driver sessions, actors, scope keys, etc.)
 * across an executor boundary. Executors created via {@link ExecutorFactory} (virtual threads,
 * fresh thread pools) do not share the calling thread's {@code ThreadLocal} state, so anything
 * relying on that state silently sees an empty/default context unless it is captured on the
 * calling thread and rebound on whichever thread actually runs the task.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} so that modules holding
 * thread-confined state (e.g. {@code testara-ui}'s driver/actor ThreadLocals) can register a
 * propagator without the module dispatching the async work (e.g. {@code testara-validation})
 * needing a compile-time dependency on it.
 */
public interface ThreadContextPropagator {

  /**
   * Snapshot whatever this propagator manages on the calling thread.
   *
   * @return an opaque snapshot to later pass to {@link #bind(Object)}
   */
  Object capture();

  /**
   * Bind a previously captured snapshot onto the current (worker) thread.
   */
  void bind(Object snapshot);

  /**
   * Detach whatever is bound on the current thread, restoring it to "nothing bound".
   */
  void unbind();
}
