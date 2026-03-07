package io.github.ygrip.testara.core.function;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test service without any @RetryableMethod annotated methods.
 * Used for testing that non-annotated classes are not proxied.
 */
public class NonRetryableService {

  private final AtomicInteger callCount = new AtomicInteger(0);

  /**
   * Normal operation without retry annotation.
   */
  public void normalOperation() {
    callCount.incrementAndGet();
  }

  /**
   * Another normal operation.
   */
  public String anotherOperation(String input) {
    callCount.incrementAndGet();
    return "processed: " + input;
  }

  /**
   * Get the call count.
   */
  public int getCallCount() {
    return callCount.get();
  }

  /**
   * Reset the counter.
   */
  public void resetCounter() {
    callCount.set(0);
  }
}

