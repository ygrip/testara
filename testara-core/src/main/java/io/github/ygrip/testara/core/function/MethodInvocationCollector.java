package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.core.model.ValueUnit;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.time.DurationParser;
import lombok.extern.log4j.Log4j2;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-scoped collector for method invocations that need retry logic.
 * Each test thread gets its own instance via @AutomationScope.
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class MethodInvocationCollector {
  private final Duration defaultTimeout;
  private final Duration defaultInterval;
  private final Set<String> retryableScanLocation;
  private final Deque<MethodInvocation> calls = new ArrayDeque<>();
  // Instance fields (not ThreadLocal) - thread safety provided by @AutomationScope
  private boolean canCollect = false;
  private boolean executeStatus = false;

  public MethodInvocationCollector(DefaultProperties properties) {
    this.defaultTimeout = properties.getRetryableTimeout();
    this.defaultInterval = properties.getRetryableInterval();
    this.retryableScanLocation = properties.getRetryableScanLocation();
  }

  public Set<String> getScanLocations() {
    return retryableScanLocation;
  }

  public void startCollecting() {
    log.debug("Starting method call collection for thread: {}", Thread.currentThread().getName());

    // Clear any existing retry context to start fresh (static call)
    InvocationContext.clear();

    // Clear any existing collected calls
    calls.clear();

    canCollect = true;
  }

  public void stopCollecting() {
    executeStatus = false;
    canCollect = false;

    // Clear retry context (static call)
    InvocationContext.clear();

    // clear calls to avoid memory leaks or replays
    calls.clear();
  }

  private void executing() {
    executeStatus = true;
  }

  public boolean isExecuting() {
    return executeStatus;
  }

  public boolean isCollecting() {
    return canCollect;
  }

  public void safelyCollect(Object instance, MethodHandle method, Object[] args, String description) throws Throwable {
    if (isCollecting() && !isExecuting()) {
      log.debug("Collecting method call: {} on {}", description, instance.getClass().getSimpleName());
      collect(instance, method, args, description);
    } else {
      log.trace("Skip collecting method {} on {} because collector is not started yet (collecting: {}, executing: {})",
          description,
          instance.getClass().getSimpleName(),
          isCollecting(),
          isExecuting());
    }
  }

  private void collect(Object instance, MethodHandle method, Object[] args, String description) {
    MethodInvocation call = new MethodInvocation(instance, method, args, description);
    calls.add(call);
  }

  public void executeAtMost(int attempts) {
    log.debug("Executing all collected method. Current thread: {}, collected methods count: {}",
        Thread.currentThread().getName(),
        calls.size());

    executing();

    for (int attempt = 0; attempt < attempts; attempt++) {
      final Deque<MethodInvocation> callsToExecute = new ArrayDeque<>(calls);
      try {
        boolean success = executeTask(callsToExecute);
        if (success) {
          log.debug("Retry attempt {} / {} success", attempt + 1, attempts);
        }
      } catch (Exception err) {
        log.warn("Retry attempt {} / {} failed. Error: {}", attempt + 1, attempts, err.getMessage());
      }
    }

    stopCollecting();
  }

  public boolean executeAll() throws Exception {
    return executeAll(defaultTimeout, defaultInterval);
  }

  private boolean executeTask(Deque<MethodInvocation> callsToExecute) throws Exception {
    log.trace("Retrying method invocations in thread: {}", Thread.currentThread().getName());
    for (MethodInvocation call : callsToExecute) {
      try {
        call.invoke();
        log.debug("Successfully executed method call: {}", call.getMethodDescription());
      } catch (Throwable err) {
        log.debug("Method call {} failed, will retry: {}", call.getMethodDescription(), err.getMessage());
        throw new Exception(err);
      }
    }
    log.debug("All {} method calls executed successfully", callsToExecute.size());
    return true; // all succeeded
  }

  public boolean executeAll(Duration timeout, Duration interval) throws Exception {
    log.debug("Executing all collected method. Current thread: {}, collected methods count: {}",
        Thread.currentThread().getName(),
        calls.size());

    if (interval.compareTo(timeout) > 0) {
      stopCollecting();
      throw new Exception("Interval cannot be greater than timeout");
    }

    // Capture the calls from the current thread before starting execution
    final Deque<MethodInvocation> callsToExecute = new ArrayDeque<>(calls);

    executing();

    AtomicReference<Throwable> lastError = new AtomicReference<>();
    try {
      Awaitility.await().pollInSameThread().atMost(timeout).pollInterval(interval).until(() -> {
        try {
          return executeTask(callsToExecute);
        } catch (Exception err) {
          lastError.set(err);
          return false;
        }
      });
      stopCollecting();
      return true;
    } catch (ConditionTimeoutException e) {
      stopCollecting();
      ValueUnit valueUnit = DurationParser.toValueUnit(timeout);
      Throwable cause = lastError.get();
      TimeoutException wrapped = new TimeoutException(
          "Retry failed after " + valueUnit.getValue() + " " + valueUnit.getUnit().name() + ". Last error: " + (
              cause != null ? cause.getMessage() : "unspecified"));
      if (cause != null) {
        wrapped.initCause(cause);
      }
      throw wrapped;
    }
  }

  public void clear() {
    calls.clear();
  }
}
