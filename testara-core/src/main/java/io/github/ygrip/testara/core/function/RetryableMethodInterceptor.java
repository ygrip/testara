package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.model.RetryableMethod;
import lombok.extern.log4j.Log4j2;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * ByteBuddy interceptor for @RetryableMethod annotations with direct collector reference.
 * 
 * This interceptor holds a direct reference to a MethodInvocationCollector.
 * Use this when you have a known collector instance (e.g., in test-scoped contexts).
 * 
 * For framework-agnostic usage with dynamic collector lookup, use
 * {@link DynamicRetryableMethodInterceptor} instead.
 * 
 * @see DynamicRetryableMethodInterceptor for dynamic collector lookup
 * @see MethodInterceptionPostProcessor for automatic proxy creation
 */
@Log4j2
public class RetryableMethodInterceptor {
  private final MethodInvocationCollector collector;

  protected RetryableMethodInterceptor(MethodInvocationCollector collector){
    this.collector = collector;
  }

  @RuntimeType
  public Object intercept(@This Object instance, @Origin Method method, @AllArguments Object[] args, @SuperCall Callable<?> superCall)
      throws Exception {
    // Check if annotation is enabled
    RetryableMethod annotation = method.getAnnotation(RetryableMethod.class);
    if (annotation == null) {
      return superCall.call();
    }

    log.trace("RetryableMethodInterceptor intercepted method: {} on class: {}",
        method.getName(), instance.getClass().getSimpleName());

    // Perform interception logic
    try {
      // Check if collector is properly initialized
      if (this.collector == null) {
        log.trace("Collector not initialized, skipping interception for: {}", method.getName());
        return superCall.call();
      }

      // If we're in execution mode, bypass all interception logic
      if (this.collector.isExecuting()) {
        log.trace("Execution mode detected, bypassing interception for: {}", method.getName());
        return superCall.call();
      }

      // Only collect if this is the first @RetryableMethod in the call chain
      boolean isFirstRetryableInChain = InvocationContext.enter(method);  // Static call
      boolean isCollecting = this.collector.isCollecting();
      
      // IMPORTANT: Always call exit() in finally to keep the stack balanced
      try {
        if (isFirstRetryableInChain && isCollecting) {
          try {
            log.trace("Collecting method call for retry: {} on {}",
                method.getName(), instance.getClass().getSimpleName());

            String description = annotation.description() != null && !annotation.description().trim().isEmpty() ?
                annotation.description() : method.getName();
            MethodHandle handle = MethodHandles.lookup().unreflect(method);

            // Collect the call for later retry
            collector.safelyCollect(instance, handle, args, description);

            // Do NOT execute the method now → return a default/null
            return getDefaultReturnValue(method.getReturnType());
          } catch (Throwable e) {
            log.trace("Failed to collect method call, executing normally: {}", e.getMessage());
            return superCall.call();
          }
        } else {
          // Nested call or not collecting → just execute normally
          log.trace("Not capturing method (isFirstRetryable: {}, isCollecting: {}), executing normally: {}",
                    isFirstRetryableInChain, isCollecting, method.getName());
          return superCall.call();
        }
      } finally {
        // Always exit to keep the stack balanced - this was missing before!
        InvocationContext.exit(method);  // Static call
      }
    } catch (Exception e) {
      // If interception fails for any reason, execute normally
      // Use trace instead of warn to reduce log spam for expected scenarios
      log.trace("Failed to perform method interception, executing normally: {}", e.getMessage());
      return superCall.call();
    }
  }

  private static Object getDefaultReturnValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType.equals(boolean.class))
      return false;
    if (returnType.equals(char.class))
      return '\0';
    if (returnType.equals(byte.class) || returnType.equals(short.class) || returnType.equals(int.class)
        || returnType.equals(long.class))
      return 0;
    if (returnType.equals(float.class))
      return 0f;
    if (returnType.equals(double.class))
      return 0d;
    return null; // should never happen
  }
}
