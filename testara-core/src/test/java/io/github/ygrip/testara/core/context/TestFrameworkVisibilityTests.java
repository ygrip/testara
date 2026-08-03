package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Regression coverage for Bug 1: {@link TestFramework} must be visible from every worker thread
 * once initialized, not just threads that happen to be children of whichever thread called
 * {@link TestFramework#initialize(TestContext)} (which is all an {@code InheritableThreadLocal}
 * would guarantee).
 * <p>
 * Runs its methods {@code SAME_THREAD} (sequentially) since one of them deliberately clears and
 * re-initializes the single, run-wide TestFramework context - letting it race with a sibling
 * method reading that same shared context concurrently would be inherently flaky, and is not
 * what this test is about.
 */
@Tag("context")
@Execution(ExecutionMode.SAME_THREAD)
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class TestFrameworkVisibilityTests extends BaseTests {

  // Created at class-load time - i.e. strictly before TestFramework.initialize() is ever called
  // for this test run (BaseTests.bootstrapFramework() only runs later, in @BeforeAll). This
  // pool's worker thread is therefore provably NOT a descendant of whichever thread later calls
  // initialize() - exactly the ForkJoinPool/executor worker thread scenario that legitimately
  // failed under the old InheritableThreadLocal-based TestFramework.
  private static final ExecutorService UNRELATED_POOL = Executors.newSingleThreadExecutor();

  @Test
  void contextIsVisibleFromAWorkerThreadThatPredatesInitialize() throws Exception {
    TestContext expected = TestFramework.context();

    Future<TestContext> observed = UNRELATED_POOL.submit(TestFramework::context);

    assertThat(observed.get(), sameInstance(expected));
  }

  @Test
  void clearMakesContextUnavailableEverywhereIncludingUnrelatedThreads() throws Exception {
    TestContext previous = TestFramework.context();
    try {
      TestFramework.clear();

      Future<Boolean> threwOnUnrelatedThread = UNRELATED_POOL.submit(() -> {
        try {
          TestFramework.context();
          return false;
        } catch (IllegalStateException expected) {
          return true;
        }
      });

      assertThat(threwOnUnrelatedThread.get(), sameInstance(Boolean.TRUE));
    } finally {
      // Restore for any tests that run after this one in the same class/run.
      TestFramework.initialize(previous);
    }
  }
}
