package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.model.RetryableMethod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test service with @RetryableMethod annotated methods.
 * Used for testing method interception functionality.
 */
public class RetryableTestService {

  private final AtomicInteger operationCallCount = new AtomicInteger(0);
  private final AtomicInteger nestedCallCount = new AtomicInteger(0);
  private final AtomicInteger failingCallCount = new AtomicInteger(0);
  private int failuresBeforeSuccess = 0;

  /**
   * Simple retryable operation that increments a counter.
   */
  @RetryableMethod(description = "Simple retryable operation")
  public void retryableOperation() {
    operationCallCount.incrementAndGet();
  }

  /**
   * Retryable operation that returns a value.
   */
  @RetryableMethod(description = "Operation with return value")
  public String retryableWithReturn() {
    operationCallCount.incrementAndGet();
    return "success";
  }

  /**
   * Retryable operation with parameters.
   */
  @RetryableMethod(description = "Operation with parameters")
  public int retryableWithParams(int a, int b) {
    operationCallCount.incrementAndGet();
    return a + b;
  }

  /**
   * Retryable operation that calls another retryable operation (nested).
   */
  @RetryableMethod(description = "Outer nested operation")
  public void nestedRetryableOuter() {
    nestedCallCount.incrementAndGet();
    nestedRetryableInner();
  }

  /**
   * Inner nested retryable operation.
   */
  @RetryableMethod(description = "Inner nested operation")
  public void nestedRetryableInner() {
    nestedCallCount.incrementAndGet();
  }

  /**
   * Retryable operation that fails a specified number of times before succeeding.
   */
  @RetryableMethod(description = "Failing operation")
  public void failingOperation() {
    int count = failingCallCount.incrementAndGet();
    if (count <= failuresBeforeSuccess) {
      throw new RuntimeException("Simulated failure #" + count);
    }
  }

  /**
   * Set how many times failingOperation should fail before succeeding.
   */
  public void setFailuresBeforeSuccess(int failures) {
    this.failuresBeforeSuccess = failures;
    this.failingCallCount.set(0);
  }

  /**
   * Get the operation call count.
   */
  public int getOperationCallCount() {
    return operationCallCount.get();
  }

  /**
   * Get the nested call count.
   */
  public int getNestedCallCount() {
    return nestedCallCount.get();
  }

  /**
   * Get the failing operation call count.
   */
  public int getFailingCallCount() {
    return failingCallCount.get();
  }

  /**
   * Reset all counters.
   */
  public void resetCounters() {
    operationCallCount.set(0);
    nestedCallCount.set(0);
    failingCallCount.set(0);
  }

  /**
   * Non-retryable method for comparison.
   */
  public void normalOperation() {
    operationCallCount.incrementAndGet();
  }
}

