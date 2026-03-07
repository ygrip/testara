package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the @RetryableMethod functionality.
 * Tests the complete flow: collection → retry → execution.
 */
@Tag("function")
@Tag("integration")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class RetryableMethodIntegrationTests extends BaseTests {

  private MethodInvocationCollector collector;
  private RetryableTestService service;

  @BeforeEach
  void setUp() {
    // Reset bootstrap state
    MethodInterceptionBootstrap.reset();
    MethodInterceptionPostProcessor.clearCache();

    // Get collector from context
    collector = TestFramework.context().get(MethodInvocationCollector.class);

    // Create test service
    service = new RetryableTestService();
    service.resetCounters();
  }

  @AfterEach
  void tearDown() {
    if (collector.isCollecting()) {
      collector.stopCollecting();
    }
    MethodInterceptionBootstrap.disable();
  }

  @Nested
  @DisplayName("Collection Phase Tests")
  class CollectionTests {

    @Test
    @DisplayName("Should start and stop collection properly")
    void shouldStartAndStopCollection() {
      assertThat(collector.isCollecting(), is(false));

      collector.startCollecting();
      assertThat(collector.isCollecting(), is(true));
      assertThat(collector.isExecuting(), is(false));

      collector.stopCollecting();
      assertThat(collector.isCollecting(), is(false));
    }

    @Test
    @DisplayName("Should clear InvocationContext when starting collection")
    void shouldClearContextOnStart() throws NoSuchMethodException {
      // Enter some context first
      Method method = RetryableTestService.class.getMethod("retryableOperation");
      InvocationContext.enter(method);

      // Start collecting should clear context
      collector.startCollecting();

      // After clearing, next entry should be first in chain
      boolean isFirst = InvocationContext.enter(method);
      assertThat(isFirst, is(true));

      collector.stopCollecting();
      InvocationContext.cleanup();
    }
  }

  @Nested
  @DisplayName("Execution Phase Tests")
  class ExecutionTests {

    @Test
    @DisplayName("Should execute all collected methods with executeAll")
    void shouldExecuteAllCollectedMethods() throws Exception {
      collector.startCollecting();

      // When collecting, the service methods would be intercepted and collected
      // For this test, we're verifying the collector state management
      assertThat(collector.isCollecting(), is(true));
      assertThat(collector.isExecuting(), is(false));

      // Execute with short timeout (should succeed immediately as there are no collected calls)
      boolean result = collector.executeAll(Duration.ofSeconds(1), Duration.ofMillis(100));

      assertThat(result, is(true));
      assertThat(collector.isCollecting(), is(false));
      assertThat(collector.isExecuting(), is(false));
    }

    @Test
    @DisplayName("Should execute at most N attempts")
    void shouldExecuteAtMostNAttempts() {
      collector.startCollecting();

      // Execute with limited attempts
      collector.executeAtMost(3);

      assertThat(collector.isCollecting(), is(false));
    }

    @Test
    @DisplayName("Should throw exception when interval is greater than timeout")
    void shouldThrowWhenIntervalGreaterThanTimeout() {
      collector.startCollecting();

      Exception exception = assertThrows(Exception.class, () ->
          collector.executeAll(Duration.ofMillis(100), Duration.ofSeconds(1))
      );

      assertThat(exception.getMessage(), containsString("Interval cannot be greater than timeout"));
    }

    @Test
    @DisplayName("Should use default timeout and interval from configuration")
    void shouldUseDefaultTimeoutAndInterval() throws Exception {
      collector.startCollecting();

      // This should use the default values from DefaultProperties
      boolean result = collector.executeAll();

      assertThat(result, is(true));
    }
  }

  @Nested
  @DisplayName("Proxy Creation Tests")
  class ProxyCreationTests {

    @Test
    @DisplayName("Should create proxy for class with @RetryableMethod")
    void shouldCreateProxyForRetryableClass() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();
      processor.configure(() -> collector, null);

      Object proxy = processor.postProcess(service, RetryableTestService.class);

      // Proxy should be a different instance
      assertThat(proxy, is(notNullValue()));
      // Should be a ByteBuddy proxy
      assertThat(proxy.getClass().getName(), containsString("$ByteBuddy$"));
    }

    @Test
    @DisplayName("Should not create proxy for class without @RetryableMethod")
    void shouldNotCreateProxyForNonRetryableClass() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();
      processor.configure(() -> collector, null);

      NonRetryableService nonRetryable = new NonRetryableService();
      Object result = processor.postProcess(nonRetryable, NonRetryableService.class);

      // Should return the same instance
      assertThat(result, is(sameInstance(nonRetryable)));
    }

    @Test
    @DisplayName("Should not proxy already proxied instances")
    void shouldNotProxyAlreadyProxied() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();
      processor.configure(() -> collector, null);

      // First proxy
      Object proxy1 = processor.postProcess(service, RetryableTestService.class);
      assertThat(proxy1.getClass().getName(), containsString("$ByteBuddy$"));

      // Second proxy attempt should return the same proxy
      @SuppressWarnings("unchecked")
      Object proxy2 = processor.postProcess(proxy1, (Class<Object>) proxy1.getClass());
      assertThat(proxy2, is(sameInstance(proxy1)));
    }
  }

  @Nested
  @DisplayName("MethodInvocation Tests")
  class MethodInvocationTests {

    @Test
    @DisplayName("Should create MethodInvocation with valid instance")
    void shouldCreateMethodInvocation() throws Throwable {
      Method method = RetryableTestService.class.getMethod("retryableOperation");
      MethodHandle handle = MethodHandles.lookup().unreflect(method);

      MethodInvocation invocation = new MethodInvocation(service, handle, new Object[]{}, "test operation");

      assertThat(invocation.getMethodDescription(), is("test operation"));
      assertThat(invocation.getClassReference(), containsString("RetryableTestService"));
    }

    @Test
    @DisplayName("Should throw on null instance")
    void shouldThrowOnNullInstance() throws Exception {
      Method method = RetryableTestService.class.getMethod("retryableOperation");
      MethodHandle handle = MethodHandles.lookup().unreflect(method);

      assertThrows(IllegalStateException.class, () ->
          new MethodInvocation(null, handle, new Object[]{}, "test")
      );
    }

    @Test
    @DisplayName("Should invoke method successfully")
    void shouldInvokeMethodSuccessfully() throws Throwable {
      Method method = RetryableTestService.class.getMethod("retryableOperation");
      MethodHandle handle = MethodHandles.lookup().unreflect(method);

      MethodInvocation invocation = new MethodInvocation(service, handle, new Object[]{}, "test operation");

      invocation.invoke();

      assertThat(service.getOperationCallCount(), is(1));
    }

    @Test
    @DisplayName("Should invoke method with parameters")
    void shouldInvokeMethodWithParams() throws Throwable {
      Method method = RetryableTestService.class.getMethod("retryableWithParams", int.class, int.class);
      MethodHandle handle = MethodHandles.lookup().unreflect(method);

      MethodInvocation invocation = new MethodInvocation(service, handle, new Object[]{5, 3}, "add operation");

      Object result = invocation.invoke();

      assertThat(result, is(8));
    }
  }

  @Nested
  @DisplayName("Thread Safety Tests")
  class ThreadSafetyTests {

    @Test
    @DisplayName("InvocationContext should be thread-isolated")
    void invocationContextShouldBeThreadIsolated() throws Exception {
      Method method = RetryableTestService.class.getMethod("retryableOperation");

      // Enter in main thread
      boolean mainIsFirst = InvocationContext.enter(method);
      assertThat(mainIsFirst, is(true));

      // Check in another thread
      Thread otherThread = new Thread(() -> {
        boolean otherIsFirst = InvocationContext.enter(method);
        // Should be first in this thread too (isolated)
        assertThat(otherIsFirst, is(true));
        InvocationContext.cleanup();
      });

      otherThread.start();
      otherThread.join();

      InvocationContext.cleanup();
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle method invocation failure gracefully")
    void shouldHandleInvocationFailure() throws Throwable {
      service.setFailuresBeforeSuccess(1);

      Method method = RetryableTestService.class.getMethod("failingOperation");
      MethodHandle handle = MethodHandles.lookup().unreflect(method);

      MethodInvocation invocation = new MethodInvocation(service, handle, new Object[]{}, "failing operation");

      // First call should fail
      assertThrows(RuntimeException.class, invocation::invoke);

      // Second call should succeed
      invocation.invoke();
      assertThat(service.getFailingCallCount(), is(2));
    }
  }
}

