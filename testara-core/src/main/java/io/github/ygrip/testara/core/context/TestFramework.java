package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.factory.ObjectFactory;
import lombok.extern.log4j.Log4j2;

/**
 * Holds the run-level {@link TestContext} for the currently executing test run.
 * <p>
 * Storage is a single, thread-safe, run-level holder rather than a {@code ThreadLocal} /
 * {@code InheritableThreadLocal}. An {@code InheritableThreadLocal} only propagates to threads
 * created <i>after</i> the initializing thread set its value — JUnit5 parallel execution runs
 * scenarios on ForkJoinPool/executor worker threads that are not children of whichever thread
 * ran {@code beforeAll}, so {@link #context()} would otherwise fail on those worker threads.
 * Using a single {@code volatile} field guarded by a lock makes the context visible to every
 * thread once {@link #initialize(TestContext)} has completed, regardless of thread ancestry.
 */
@Log4j2
public final class TestFramework {
  private static volatile TestContext RUN_CONTEXT;
  private static final Object INIT_LOCK = new Object();

  private TestFramework() {
  }

  public static void initialize(TestContext ctx) {
    synchronized (INIT_LOCK) {
      RUN_CONTEXT = ctx;
    }
    log.debug("TestFramework initialized with context: {}",
        ctx == null ? null : ctx.getClass().getSimpleName());
  }

  public static TestContext context() {
    TestContext ctx = RUN_CONTEXT;
    if (ctx == null) {
      throw new IllegalStateException("TestContext not initialized");
    }
    return ctx;
  }

  public static TestConfiguration configuration() {
    return context().configuration();
  }

  public static ObjectFactory factory() {
    return context().factory();
  }

  public static void clear() {
    synchronized (INIT_LOCK) {
      RUN_CONTEXT = null;
    }
    log.debug("TestFramework context cleared");
  }
}
