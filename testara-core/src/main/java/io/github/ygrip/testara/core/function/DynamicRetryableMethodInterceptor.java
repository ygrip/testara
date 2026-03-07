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
import java.util.function.Supplier;

/**
 * ByteBuddy interceptor that uses dynamic collector lookup.
 * 
 * This interceptor resolves the MethodInvocationCollector at invocation time
 * via a supplier, allowing it to work with TEST-scoped collectors while being
 * a singleton itself.
 * 
 * The key difference from RetryableMethodInterceptor:
 * - RetryableMethodInterceptor holds a direct reference to a collector
 * - DynamicRetryableMethodInterceptor uses a supplier to look up the collector each time
 * 
 * This design supports:
 * - Framework-agnostic operation (no Spring dependency)
 * - TEST-scoped collectors (each test gets its own collector instance)
 * - Thread isolation in parallel test execution
 */
@Log4j2
public class DynamicRetryableMethodInterceptor {
  
  private final Supplier<MethodInvocationCollector> collectorSupplier;

  /**
   * Create an interceptor with dynamic collector lookup.
   * 
   * @param collectorSupplier supplier that provides the collector for the current test/thread
   */
  public DynamicRetryableMethodInterceptor(Supplier<MethodInvocationCollector> collectorSupplier) {
    this.collectorSupplier = collectorSupplier;
  }

  @RuntimeType
  public Object intercept(@This Object instance, @Origin Method method, @AllArguments Object[] args, @SuperCall Callable<?> superCall)
      throws Exception {
    
    // Check if annotation is present and enabled
    RetryableMethod annotation = method.getAnnotation(RetryableMethod.class);
    if (annotation == null) {
      return superCall.call();
    }

    log.trace("DynamicRetryableMethodInterceptor intercepted method: {} on class: {}",
        method.getName(), instance.getClass().getSimpleName());

    try {
      // Get the collector for the current test/thread
      MethodInvocationCollector collector = collectorSupplier.get();
      
      // Check if collector is available
      if (collector == null) {
        log.trace("Collector not available, executing method directly: {}", method.getName());
        return superCall.call();
      }

      // If we're in execution mode, bypass all interception logic
      if (collector.isExecuting()) {
        log.trace("Execution mode detected, bypassing interception for: {}", method.getName());
        return superCall.call();
      }

      // Only collect if this is the first @RetryableMethod in the call chain
      boolean isFirstRetryableInChain = InvocationContext.enter(method);
      boolean isCollecting = collector.isCollecting();
      
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
        InvocationContext.exit(method);
      }
    } catch (Exception e) {
      // If context is not available or lookup fails, execute normally
      log.trace("Failed to perform interception, executing normally: {}", e.getMessage());
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

