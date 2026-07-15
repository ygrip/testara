package io.github.ygrip.testara.engine;

import io.github.ygrip.testara.engine.context.TestaraCucumberEngineExecutionContext;
import io.github.ygrip.testara.engine.descriptor.TestaraCucumberEngineDescriptor;
import io.github.ygrip.testara.engine.descriptor.TestaraDiscoverySelectorResolver;
import io.github.ygrip.testara.engine.executor.AdaptiveHierarchicalTestExecutorService;
import io.github.ygrip.testara.engine.executor.VirtualThreadHierarchicalTestExecutorService;
import io.github.ygrip.testara.engine.option.MergedConfigurationDiscoveryRequest;
import io.github.ygrip.testara.engine.option.TestaraConfigurationParameters;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.config.PrefixedConfigurationParameters;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.discovery.DiscoveryIssueReporter;
import org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutorService;

import static io.cucumber.core.options.Constants.FEATURES_PROPERTY_NAME;
import static org.junit.platform.engine.support.discovery.DiscoveryIssueReporter.deduplicating;
import static org.junit.platform.engine.support.discovery.DiscoveryIssueReporter.forwarding;

@Log4j2
public final class TestaraCucumberTestEngine extends HierarchicalTestEngine<TestaraCucumberEngineExecutionContext> {
  static final String PARALLEL_CONFIG_PREFIX = "cucumber.execution.parallel.config.";

  private static TestSource createEngineTestSource(EngineDiscoveryRequest discoveryRequest) {
    ConfigurationParameters configuration = discoveryRequest.getConfigurationParameters();
    if (configuration.get(FEATURES_PROPERTY_NAME).isPresent()) {
      return ClassSource.from(TestaraCucumberTestEngine.class);
    }
    return null;
  }

  private static TestSource createEngineTestSource(ConfigurationParameters configurationParameters) {
    // Workaround. Test Engines do not normally have test source.
    // Maven does not count tests that do not have a ClassSource somewhere
    // in the test descriptor tree.
    // Gradle will report all tests as coming from an "Unknown Class"
    // See: https://github.com/cucumber/cucumber-jvm/pull/2498
    if (configurationParameters.get(FEATURES_PROPERTY_NAME).isPresent()) {
      return ClassSource.from(TestaraCucumberTestEngine.class);
    }
    return null;
  }

  @Override
  protected TestaraCucumberEngineExecutionContext createExecutionContext(ExecutionRequest executionRequest) {
    return new TestaraCucumberEngineExecutionContext(executionRequest);
  }

  @Override
  public String getId() {
    return "testara-cucumber";
  }

  @Override
  public TestDescriptor discover(EngineDiscoveryRequest engineDiscoveryRequest, UniqueId uniqueId) {
    log.debug("Testara Junit5 discover tests with engine id {} [PID:{}]", uniqueId, ProcessHandle.current().pid());
    EngineDiscoveryRequest request = new MergedConfigurationDiscoveryRequest(engineDiscoveryRequest);
    ConfigurationParameters configurationParameters = request.getConfigurationParameters();
    TestSource testSource = createEngineTestSource(configurationParameters);
    DiscoveryIssueReporter issueReporter =
        deduplicating(forwarding(request.getDiscoveryListener(), uniqueId));

    TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(configurationParameters);
    TestaraCucumberEngineDescriptor engineDescriptor = new TestaraCucumberEngineDescriptor(uniqueId, options, testSource);

    TestaraDiscoverySelectorResolver resolver = new TestaraDiscoverySelectorResolver();
    resolver.resolveSelectors(request, engineDescriptor);
    return engineDescriptor;
  }

  @Override
  protected HierarchicalTestExecutorService createExecutorService(ExecutionRequest request) {
    ConfigurationParameters config = TestaraConfigurationParameters.merge(request.getConfigurationParameters());

    if (!config.getBoolean("cucumber.execution.parallel.enabled").orElse(false)) {
      log.info("Parallel execution disabled - using default executor");
      return super.createExecutorService(request);
    }

    // Check if virtual threads are enabled (Java 21+ Project Loom)
    boolean virtualThreadsEnabled =
        config.getBoolean("cucumber.execution.parallel.virtual-thread.enabled").orElse(false);

    if (virtualThreadsEnabled) {
      log.info("Virtual threads enabled - creating VirtualThreadHierarchicalTestExecutorService");
      return createVirtualThreadExecutorService(config);
    }

    // Check if dynamic parallelism is enabled (platform threads)
    String strategy = config.get("cucumber.execution.parallel.config.strategy").orElse("fixed");

    if ("dynamic".equalsIgnoreCase(strategy)) {
      log.info("Dynamic parallelism enabled - creating AdaptiveHierarchicalTestExecutorService (platform threads)");
      return createDynamicExecutorService(config);
    } else {
      log.info("Fixed parallelism - creating standard ForkJoinPoolHierarchicalTestExecutorService");
      return new ForkJoinPoolHierarchicalTestExecutorService(new PrefixedConfigurationParameters(config,
          PARALLEL_CONFIG_PREFIX));
    }
  }

  /**
   * Create a virtual thread executor service (Java 21+ Project Loom)
   * Uses VirtualThreadHierarchicalTestExecutorService
   * <p>
   * Respects the parallel strategy configuration:
   * - For "fixed" strategy: uses cucumber.execution.parallel.config.fixed.parallelism
   * - For "dynamic" strategy: uses cucumber.execution.parallel.virtual-thread.max-threads
   * </p>
   */
  private HierarchicalTestExecutorService createVirtualThreadExecutorService(ConfigurationParameters config) {
    try {
      // Check if Java 21+ virtual threads are supported
      if (!isVirtualThreadsSupported()) {
        log.warn("Virtual threads not supported (Java < 21) - falling back to dynamic platform threads");
        return createDynamicExecutorService(config);
      }

      // Create options wrapper to access virtual thread configuration
      TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(config);

      // Use effective parallelism based on strategy (fixed vs dynamic)
      int effectiveParallelism = options.getEffectiveVirtualThreadParallelism();
      String strategy = options.getParallelStrategy();
      String namePrefix = options.getVirtualThreadNamePrefix();

      log.debug("Creating virtual thread executor: parallelism={}, strategy={}, prefix={}",
          effectiveParallelism, strategy, namePrefix);

      // Create virtual thread executor service
      // Pass the original config (not wrapped) to avoid any lifecycle issues
      return new VirtualThreadHierarchicalTestExecutorService(effectiveParallelism, namePrefix);

    } catch (Exception e) {
      log.error("Failed to create virtual thread executor, falling back to dynamic platform threads", e);
      return createDynamicExecutorService(config);
    }
  }

  /**
   * Create a dynamic executor service that adapts parallelism based on workload
   * Uses AdaptiveHierarchicalTestExecutorService with ForkJoinPool
   */
  private HierarchicalTestExecutorService createDynamicExecutorService(ConfigurationParameters config) {
    try {
      // Create options wrapper
      TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(config);

      // Create dynamic parallelism manager
      DynamicParallelismManager parallelismManager = new DynamicParallelismManager(options);

      // Calculate initial parallelism
      int initialParallelism = parallelismManager.calculateInitialParallelism();

      log.debug("Creating dynamic platform thread executor: initial={}, min={}, max={}",
          initialParallelism,
          parallelismManager.getMinParallelism(),
          parallelismManager.getMaxParallelism());

      // Create adaptive executor service with runtime scaling (platform threads)
      return new AdaptiveHierarchicalTestExecutorService(initialParallelism,
          parallelismManager,
          config,
          PARALLEL_CONFIG_PREFIX);

    } catch (Exception e) {
      log.error("Failed to create dynamic executor, falling back to fixed ForkJoinPool", e);
      return new ForkJoinPoolHierarchicalTestExecutorService(new PrefixedConfigurationParameters(config,
          PARALLEL_CONFIG_PREFIX));
    }
  }

  /**
   * Check if virtual threads are supported (Java 21+)
   */
  private boolean isVirtualThreadsSupported() {
    try {
      // Try to access Thread.ofVirtual() which is available in Java 21+
      Thread.class.getMethod("ofVirtual");
      log.debug("Virtual threads are supported (Java 21+)");
      return true;
    } catch (NoSuchMethodException e) {
      log.debug("Virtual threads not supported (Java < 21)");
      return false;
    }
  }
}

