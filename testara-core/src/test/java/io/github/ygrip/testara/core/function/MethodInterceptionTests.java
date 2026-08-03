package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.factory.InstancePostProcessor;
import io.github.ygrip.testara.core.factory.PostProcessorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests for the method interception functionality.
 * Verifies ByteBuddy proxy creation and @RetryableMethod interception.
 */
@Tag("function")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
@Isolated("Mutates the global post-processor registry")
@Execution(ExecutionMode.SAME_THREAD)
public class MethodInterceptionTests extends BaseTests {

  @BeforeEach
  void setUp() {
    // Reset state before each test
    MethodInterceptionBootstrap.reset();
    MethodInterceptionPostProcessor.clearCache();
  }

  @AfterEach
  void tearDown() {
    MethodInterceptionBootstrap.disable();
  }

  @Nested
  @DisplayName("MethodInterceptionBootstrap Tests")
  class BootstrapTests {

    @Test
    @DisplayName("Should enable method interception successfully")
    void shouldEnableInterception() {
      assertThat(MethodInterceptionBootstrap.isEnabled(), is(false));

      MethodInterceptionBootstrap.enable();

      assertThat(MethodInterceptionBootstrap.isEnabled(), is(true));
      assertThat(MethodInterceptionBootstrap.getPostProcessor(), is(notNullValue()));
    }

    @Test
    @DisplayName("Should be idempotent - calling enable multiple times is safe")
    void shouldBeIdempotent() {
      MethodInterceptionBootstrap.enable();
      MethodInterceptionPostProcessor first = MethodInterceptionBootstrap.getPostProcessor();

      MethodInterceptionBootstrap.enable();
      MethodInterceptionPostProcessor second = MethodInterceptionBootstrap.getPostProcessor();

      assertThat(first, is(sameInstance(second)));
    }

    @Test
    @DisplayName("Should enable with package whitelist")
    void shouldEnableWithWhitelist() {
      Set<String> whitelist = Set.of("io.github.ygrip.testara");

      MethodInterceptionBootstrap.enable(whitelist);

      assertThat(MethodInterceptionBootstrap.isEnabled(), is(true));
    }

    @Test
    @DisplayName("Should disable and clean up resources")
    void shouldDisableAndCleanup() {
      MethodInterceptionBootstrap.enable();
      assertThat(MethodInterceptionBootstrap.isEnabled(), is(true));

      MethodInterceptionBootstrap.disable();

      assertThat(MethodInterceptionBootstrap.isEnabled(), is(false));
      assertThat(MethodInterceptionBootstrap.getPostProcessor(), is(nullValue()));
    }
  }

  @Nested
  @DisplayName("PostProcessorRegistry Tests")
  class RegistryTests {

    @BeforeEach
    void setUpRegistry() {
      // Clear registry before each test to ensure test isolation
      // This prevents state leakage from other tests that register processors
      PostProcessorRegistry.instance().clear();
    }

    @AfterEach
    void tearDownRegistry() {
      // Clear registry after each test to avoid affecting other tests
      PostProcessorRegistry.instance().clear();
    }

    @Test
    @DisplayName("Should register and apply post processors")
    void shouldRegisterAndApplyPostProcessors() {
      AtomicInteger callCount = new AtomicInteger(0);

      // Create a simple test post processor
      TestPostProcessor testProcessor = new TestPostProcessor(callCount);
      PostProcessorRegistry.instance().register(testProcessor);

      // Apply to an instance
      String testInstance = "test";
      String result = PostProcessorRegistry.instance().applyPostProcessors(testInstance, String.class);

      assertThat(result, is(notNullValue()));
      assertThat(callCount.get(), is(1));
    }

    @Test
    @DisplayName("Should check if processor exists for type")
    void shouldCheckProcessorExists() {
      TestPostProcessor testProcessor = new TestPostProcessor(new AtomicInteger());
      PostProcessorRegistry.instance().register(testProcessor);

      assertThat(PostProcessorRegistry.instance().hasProcessorFor(String.class), is(true));
    }

    @Test
    @DisplayName("Should handle null instance gracefully")
    void shouldHandleNullInstance() {
      String result = PostProcessorRegistry.instance().applyPostProcessors(null, String.class);

      assertThat(result, is(nullValue()));
    }
  }

  @Nested
  @DisplayName("InvocationContext Tests")
  class InvocationContextTests {

    @Test
    @DisplayName("Should track method entry and exit")
    void shouldTrackMethodEntryAndExit() throws NoSuchMethodException {
      InvocationContext.clear();

      java.lang.reflect.Method method = RetryableTestService.class.getMethod("retryableOperation");

      boolean isFirst = InvocationContext.enter(method);
      assertThat(isFirst, is(true));

      // Second entry should not be first
      boolean isSecond = InvocationContext.enter(method);
      assertThat(isSecond, is(false));

      InvocationContext.exit(method);
      InvocationContext.exit(method);
    }

    @Test
    @DisplayName("Should clear context properly")
    void shouldClearContext() throws NoSuchMethodException {
      java.lang.reflect.Method method = RetryableTestService.class.getMethod("retryableOperation");

      InvocationContext.enter(method);
      InvocationContext.clear();

      // After clear, next entry should be first
      boolean isFirst = InvocationContext.enter(method);
      assertThat(isFirst, is(true));

      InvocationContext.cleanup();
    }

    @Test
    @DisplayName("Should cleanup ThreadLocal properly")
    void shouldCleanupThreadLocal() throws NoSuchMethodException {
      java.lang.reflect.Method method = RetryableTestService.class.getMethod("retryableOperation");

      InvocationContext.enter(method);
      InvocationContext.cleanup();

      // After cleanup, should be able to use again
      boolean isFirst = InvocationContext.enter(method);
      assertThat(isFirst, is(true));

      InvocationContext.cleanup();
    }
  }

  @Nested
  @DisplayName("MethodInterceptionPostProcessor Tests")
  class PostProcessorTests {

    @Test
    @DisplayName("Should support classes with @RetryableMethod")
    void shouldSupportRetryableClasses() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      assertThat(processor.supports(RetryableTestService.class), is(true));
    }

    @Test
    @DisplayName("Should not support classes without @RetryableMethod")
    void shouldNotSupportNonRetryableClasses() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      assertThat(processor.supports(NonRetryableService.class), is(false));
    }

    @Test
    @DisplayName("Should not support interfaces")
    void shouldNotSupportInterfaces() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      assertThat(processor.supports(Runnable.class), is(false));
    }

    @Test
    @DisplayName("Should not support primitive types")
    void shouldNotSupportPrimitiveTypes() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      assertThat(processor.supports(int.class), is(false));
    }

    @Test
    @DisplayName("Should not support Java system classes")
    void shouldNotSupportSystemClasses() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      assertThat(processor.supports(String.class), is(false));
      assertThat(processor.supports(java.util.ArrayList.class), is(false));
    }

    @Test
    @DisplayName("Should have high priority")
    void shouldHaveHighPriority() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      assertThat(processor.priority(), is(100));
    }

    @Test
    @DisplayName("Should handle null instance")
    void shouldHandleNullInstance() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      Object result = processor.postProcess(null, Object.class);

      assertThat(result, is(nullValue()));
    }

    @Test
    @DisplayName("Should respect package whitelist")
    void shouldRespectPackageWhitelist() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();
      processor.configure(null, Set.of("com.different.package"));

      // Our test class is in io.github.ygrip.testara.core.function
      assertThat(processor.supports(RetryableTestService.class), is(false));
    }

    @Test
    @DisplayName("Should clear cache on shutdown")
    void shouldClearCacheOnShutdown() {
      MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

      // Trigger cache population
      processor.supports(RetryableTestService.class);

      // Shutdown should clear cache
      processor.shutdown();

      // No direct way to verify cache is cleared, but this shouldn't throw
      processor.supports(RetryableTestService.class);
    }
  }

  @Nested
  @DisplayName("MethodInvocationCollector Tests")
  class CollectorTests {

    @Test
    @DisplayName("Should start and stop collecting")
    void shouldStartAndStopCollecting() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      assertThat(collector.isCollecting(), is(false));

      collector.startCollecting();
      assertThat(collector.isCollecting(), is(true));

      collector.stopCollecting();
      assertThat(collector.isCollecting(), is(false));
    }

    @Test
    @DisplayName("Should track execution state")
    void shouldTrackExecutionState() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      assertThat(collector.isExecuting(), is(false));

      collector.startCollecting();
      assertThat(collector.isExecuting(), is(false));

      collector.stopCollecting();
    }

    @Test
    @DisplayName("Should return scan locations from configuration")
    void shouldReturnScanLocations() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      Set<String> locations = collector.getScanLocations();
      assertThat(locations, is(notNullValue()));
    }

    @Test
    @DisplayName("Should clear collected calls")
    void shouldClearCollectedCalls() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      collector.startCollecting();
      collector.clear();
      collector.stopCollecting();

      // Should not throw
    }
  }

  @Nested
  @DisplayName("DynamicRetryableMethodInterceptor Tests")
  class DynamicInterceptorTests {

    @Test
    @DisplayName("Should create interceptor with supplier")
    void shouldCreateWithSupplier() {
      AtomicInteger callCount = new AtomicInteger(0);

      DynamicRetryableMethodInterceptor interceptor = new DynamicRetryableMethodInterceptor(() -> {
        callCount.incrementAndGet();
        return null; // Return null collector for this test
      });

      assertThat(interceptor, is(notNullValue()));
    }
  }

  @Nested
  @DisplayName("RetryableMethodPostProcessor Integration Tests")
  class RetryableMethodPostProcessorTests {

    @Test
    @DisplayName("Should auto-enable on construction")
    void shouldAutoEnableOnConstruction() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      RetryableMethodPostProcessor processor = new RetryableMethodPostProcessor(collector);

      assertThat(processor.isEnabled(), is(true));
    }

    @Test
    @DisplayName("Should provide access to collector")
    void shouldProvideAccessToCollector() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      RetryableMethodPostProcessor processor = new RetryableMethodPostProcessor(collector);

      assertThat(processor.getMethodInvocationCollector(), is(sameInstance(collector)));
    }

    @Test
    @DisplayName("Should provide access to interception post processor")
    void shouldProvideAccessToInterceptionPostProcessor() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

      RetryableMethodPostProcessor processor = new RetryableMethodPostProcessor(collector);

      assertThat(processor.getInterceptionPostProcessor(), is(notNullValue()));
    }

    @Test
    @DisplayName("Legacy API should delegate to new implementation")
    void legacyApiShouldDelegate() {
      MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);
      RetryableMethodPostProcessor processor = new RetryableMethodPostProcessor(collector);

      NonRetryableService service = new NonRetryableService();

      @SuppressWarnings("deprecation")
      Object result = processor.postProcessAfterInitialization(service, "testBean");

      // Non-retryable service should be returned as-is
      assertThat(result, is(sameInstance(service)));
    }

    @Test
    @DisplayName("Should reuse the already-registered interception post processor instead of registering a duplicate")
    void shouldReuseAlreadyRegisteredInterceptionPostProcessor() {
      PostProcessorRegistry.instance().clear();
      try {
        MethodInvocationCollector collector = TestFramework.context().get(MethodInvocationCollector.class);

        // RetryableMethodPostProcessor is TEST-scoped, so a new instance is constructed per
        // scenario. Before the fix, each construction created and registered a brand new
        // MethodInterceptionPostProcessor, and PostProcessorRegistry.register() only de-dupes
        // by reference identity - so duplicates piled up in the static registry and each
        // independently generated its own proxy Class for the same target, tripping
        // resolveImplementationType()'s "multiple processors disagree" check.
        RetryableMethodPostProcessor first = new RetryableMethodPostProcessor(collector);
        int sizeAfterFirst = PostProcessorRegistry.instance().size();

        RetryableMethodPostProcessor second = new RetryableMethodPostProcessor(collector);

        assertThat(second.getInterceptionPostProcessor(), is(sameInstance(first.getInterceptionPostProcessor())));
        assertThat(PostProcessorRegistry.instance().size(), is(sizeAfterFirst));
      } finally {
        PostProcessorRegistry.instance().clear();
      }
    }
  }

  // ========================================================================
  // Test Fixtures
  // ========================================================================

  /**
   * Simple post processor for testing
   */
  static class TestPostProcessor implements InstancePostProcessor {
    private final AtomicInteger callCount;

    TestPostProcessor(AtomicInteger callCount) {
      this.callCount = callCount;
    }

    @Override
    public <T> T postProcess(T instance, Class<T> instanceType) {
      callCount.incrementAndGet();
      return instance;
    }
  }
}