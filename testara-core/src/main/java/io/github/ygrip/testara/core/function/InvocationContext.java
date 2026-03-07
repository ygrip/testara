package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.model.RetryableMethod;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Static utility for tracking method invocation chains per thread.
 * Uses ThreadLocal for thread isolation - more memory efficient than Spring thread-scoped bean.
 * 
 * IMPORTANT: Call cleanup() when thread completes to prevent memory leaks.
 */
@Log4j2
public final class InvocationContext {
  
  // ThreadLocal for thread-safe isolation
  private static final ThreadLocal<Deque<Method>> ALL_CALL_STACK = 
      ThreadLocal.withInitial(ArrayDeque::new);
  
  private static final ThreadLocal<Deque<Method>> RETRYABLE_CALL_STACK = 
      ThreadLocal.withInitial(ArrayDeque::new);
  
  // Utility class - prevent instantiation
  private InvocationContext() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Enter a method call context
   * @param method The method being called
   * @return true if this is the first @RetryableMethod in the call chain
   */
  public static boolean enter(Method method) {
    Deque<Method> allStack = ALL_CALL_STACK.get();
    Deque<Method> retryableStack = RETRYABLE_CALL_STACK.get();
    
    allStack.push(method);
    
    // Check if this method is annotated with @RetryableMethod
    boolean isRetryableMethod = method.isAnnotationPresent(RetryableMethod.class);
    boolean isFirstRetryableInChain = false;
    
    if (isRetryableMethod) {
      retryableStack.push(method);
      isFirstRetryableInChain = retryableStack.size() == 1; // First @RetryableMethod in the chain
    }

    log.trace(
        "InvocationContext.enter({}) - allStack: {}, retryableStack: {}, isRetryableMethod: {}, isFirstRetryableInChain: {}",
        method.getName(),
        allStack.size(),
        retryableStack.size(),
        isRetryableMethod,
        isFirstRetryableInChain);
    
    return isFirstRetryableInChain;
  }

  /**
   * Exit a method call context
   * @param method The method being exited
   */
  public static void exit(Method method) {
    Deque<Method> allStack = ALL_CALL_STACK.get();
    Deque<Method> retryableStack = RETRYABLE_CALL_STACK.get();
    
    if (!allStack.isEmpty()) {
      allStack.pop();
    }
    
    // Only pop from retryable stack if this method is annotated
    if (method.isAnnotationPresent(RetryableMethod.class) && !retryableStack.isEmpty()) {
      retryableStack.pop();
    }
  }
  
  /**
   * Clear all stacks for current thread - useful when starting a new retry collection cycle
   */
  public static void clear() {
    ALL_CALL_STACK.get().clear();
    RETRYABLE_CALL_STACK.get().clear();
    log.trace("InvocationContext cleared for thread: {}", Thread.currentThread().getName());
  }
  
  /**
   * IMPORTANT: Cleanup ThreadLocal to prevent memory leaks
   * Must be called when thread completes (typically in AutomationScope or test hooks)
   */
  public static void cleanup() {
    ALL_CALL_STACK.remove();
    RETRYABLE_CALL_STACK.remove();
    log.trace("InvocationContext cleaned up for thread: {}", Thread.currentThread().getName());
  }
}
