package io.github.ygrip.testara.core.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage: {@code ValidatorHelper.getValidationResult()} dispatched each validation
 * onto a fresh virtual thread via {@code CompletableFuture.supplyAsync}, which does not share the
 * calling (Cucumber step) thread's ThreadLocal state - so anything reading current driver/actor
 * state during validation (e.g. {@code ElementVisibilityValidation}) silently saw an empty
 * context and failed with "No current driver session". {@link ExecutorFactory#withPropagatedContext}
 * fixes that generically via the {@link ThreadContextPropagator} SPI - this test proves the
 * capture/bind/unbind lifecycle actually crosses a real thread boundary.
 */
class ExecutorFactoryThreadContextPropagationTest {

  @AfterEach
  void clearThreadLocal() {
    FakeThreadContextPropagator.VALUE.remove();
  }

  @Test
  void propagatesCapturedContextOntoAFreshThreadAndUnbindsWhenTheTaskCompletes() throws InterruptedException {
    FakeThreadContextPropagator.VALUE.set("caller-value");

    AtomicReference<String> seenDuringTask = new AtomicReference<>();
    AtomicReference<String> seenAfterTaskOnSameThread = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);

    var wrapped = ExecutorFactory.withPropagatedContext(() -> {
      seenDuringTask.set(FakeThreadContextPropagator.VALUE.get());
      return null;
    });

    // A brand-new platform thread never had FakeThreadContextPropagator.VALUE set - if wrapped
    // still observes "caller-value" inside the task, that value crossed the thread boundary via
    // bind(), not by accident of thread reuse.
    Thread worker = new Thread(() -> {
      wrapped.get();
      seenAfterTaskOnSameThread.set(FakeThreadContextPropagator.VALUE.get());
      done.countDown();
    });
    worker.start();

    assertTrue(done.await(5, TimeUnit.SECONDS), "worker thread must complete");
    assertEquals("caller-value", seenDuringTask.get(),
        "the task running on the worker thread must see the calling thread's captured context");
    assertNull(seenAfterTaskOnSameThread.get(),
        "the propagator must unbind after the task so it doesn't leak into whatever runs next on that thread");
  }
}
