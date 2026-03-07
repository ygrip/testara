package io.github.ygrip.testara.engine;

import io.github.ygrip.testara.core.concurrency.ExecutorFactory;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.engine.context.TestaraCucumberEngineExecutionContext;
import io.github.ygrip.testara.engine.descriptor.TestaraCucumberEngineDescriptor;
import io.github.ygrip.testara.engine.descriptor.TestaraFeatureOrigin;
import io.github.ygrip.testara.engine.descriptor.TestaraFeatureResolver;
import io.github.ygrip.testara.engine.descriptor.TestaraNodeDescriptor;
import io.github.ygrip.testara.engine.executor.BoundedVirtualExecutor;
import io.github.ygrip.testara.engine.extension.TestaraExtension;
import io.github.ygrip.testara.engine.extension.TestaraExtensionContext;
import io.github.ygrip.testara.engine.model.RerunStrategy;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import io.github.ygrip.testara.engine.support.TestDescriptorOrderUtils;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.runtime.CucumberExecutionContext;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.Node;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static org.junit.platform.engine.UniqueId.forEngine;

/**
 * Manages the execution of deferred reruns for failed scenarios
 * This implementation properly executes scenarios using separate CucumberExecutionContext instances
 * to guarantee multiple report files (cucumber-1.json, cucumber-2.json, etc.)
 * Uses FailedScenariosListener for tracking failed scenarios instead of separate result tracker
 * Enhanced with parallel execution support and critical scenario promotion rules
 */
@Log4j2
public final class DeferredRerunManager {

  private final TestaraCucumberEngineOptions options;
  private final List<File> rerunReportFiles;
  // Bounded executor for parallel scenario execution - respects parallel configuration
  // Can be either virtual threads or platform threads depending on configuration
  private final ExecutorService scenarioExecutor;
  private final ExecutorService worker;
  private final boolean useVirtualThreads;
  private boolean deferredRerunExecuted = false;
  
  // Captured TestContext for propagation to rerun threads
  // Thread pool threads may be created before framework initialization,
  // so we need to manually propagate the context
  private volatile TestContext capturedTestContext;

  public DeferredRerunManager(TestaraCucumberEngineOptions options) {
    this.options = options;
    this.rerunReportFiles = new ArrayList<>();

    // Check if virtual threads are enabled
    this.useVirtualThreads = options.getConfigurationParameters()
        .getBoolean("cucumber.execution.parallel.virtual-thread.enabled")
        .orElse(false) && isVirtualThreadsSupported();

    this.worker = ExecutorFactory.createSafeCachedThreadPool("testara-rerun");

    // Create executor for parallel scenario execution based on virtual thread availability
    int parallelism = getConfiguredParallelism();

    if (useVirtualThreads) {
      // Use unbounded virtual threads for deferred reruns
      this.scenarioExecutor = new BoundedVirtualExecutor("deferred-rerun-virtual", parallelism);
      log.debug(
          "Created VIRTUAL THREAD deferred rerun executors: asyncWrapper=CachedThreadPool, scenarios=VirtualThreads(unbounded)");
    } else {
      // Use bounded platform threads
      this.scenarioExecutor = ExecutorFactory.createSafeFixedThreadPool(parallelism, "deferred-rerun-scenario");
      log.debug(
          "Created PLATFORM THREAD deferred rerun executors: asyncWrapper=CachedThreadPool, scenarios=FixedThreadPool({})",
          parallelism);
    }
  }

  /**
   * Check if virtual threads are supported (Java 21+)
   */
  private boolean isVirtualThreadsSupported() {
    try {
      Thread.class.getMethod("ofVirtual");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * Get the configured parallelism for rerun scenario execution
   * Falls back to sensible defaults if not configured
   */
  private int getConfiguredParallelism() {
    ConfigurationParameters config = options.getConfigurationParameters();

    // Try to get parallelism from configuration (in order of priority)
    // 1. Fixed parallelism configuration
    Integer parallelism =
        config.get("cucumber.execution.parallel.config.fixed.parallelism", Integer::parseInt).orElse(null);

    // 2. Dynamic max parallelism
    if (parallelism == null) {
      parallelism = config.get("cucumber.execution.parallel.config.dynamic.max", Integer::parseInt).orElse(null);
    }

    // 3. Fall back to available processors
    if (parallelism == null) {
      parallelism = Runtime.getRuntime().availableProcessors();
      log.debug("No parallelism configured, using available processors: {}", parallelism);
    }

    parallelism = Math.max(1, parallelism);

    log.debug("Configured rerun parallelism: {}", parallelism);
    return parallelism;
  }

  /**
   * Execute deferred rerun for failed scenarios using separate execution contexts for each attempt
   * This guarantees that each rerun attempt generates its own cucumber-N.json report file
   */
  public CompletableFuture<RerunResult> executeDeferred(List<FeatureWithLines> failedFeatures,
      TestaraCucumberEngineExecutionContext originalContext) {
    RerunStrategy strategy = options.getRerunStrategy();

    if (strategy == RerunStrategy.COMBINE) {
      log.debug("Executing combined deferred rerun phase for {} failed features (after immediate rerun attempts)",
          failedFeatures.size());
    } else {
      log.debug("Executing deferred rerun with strategy: {} for {} failed features", strategy, failedFeatures.size());
    }

    return switch (strategy) {
      case DEFERRED -> executeDeferredRerun(failedFeatures, originalContext);
      case COMBINE ->
        // For COMBINE strategy, execute deferred rerun for scenarios that failed after immediate rerun
          executeDeferredRerun(failedFeatures, originalContext);
      default ->
        // For NONE and IMMEDIATE strategies, no deferred rerun is needed
          CompletableFuture.completedFuture(new RerunResult(0,
              0,
              "No deferred rerun needed for strategy: " + strategy));
    };
  }

  /**
   * Execute the actual deferred rerun using separate CucumberExecutionContext for each attempt
   * This ensures each attempt generates its own cucumber-N.json report file
   * GUARANTEED to return a RerunResult - never throws exceptions
   */
  private CompletableFuture<RerunResult> executeDeferredRerun(List<FeatureWithLines> failedFeatures,
      TestaraCucumberEngineExecutionContext originalContext) {
    if (failedFeatures.isEmpty()) {
      log.debug("No failed scenarios to rerun");
      return CompletableFuture.completedFuture(new RerunResult(0, 0, "No failed scenarios to rerun"));
    }

    // CRITICAL: Capture the TestContext from the current thread for propagation
    // Thread pool threads may have been created before framework initialization
    captureTestContext();

    log.debug("Starting deferred rerun for {} failed features", failedFeatures.size());
    deferredRerunExecuted = true;

    try {
      return CompletableFuture.supplyAsync(() -> executeRerunAttempts(failedFeatures, originalContext), worker);
    } catch (Throwable t) {
      log.error("Failed to execute deferred rerun: {}", t.getMessage());
      // Calculate total scenarios for error reporting
      int totalScenarios = failedFeatures.stream().map(fail -> fail.lines().size()).reduce(0, Integer::sum);
      return CompletableFuture.completedFuture(new RerunResult(totalScenarios,
          0,
          "Deferred rerun failed: " + t.getMessage()));
    }
  }

  /**
   * Execute multiple rerun attempts for failed scenarios using separate CucumberExecutionContext for each attempt
   * This guarantees separate cucumber-N.json files for each rerun attempt
   * Enhanced with hybrid parallel/sequential execution strategy:
   * - Early attempts (1 to maxRetries-1): Run in parallel for speed
   * - Final attempt (maxRetries): Run sequentially to handle flaky tests caused by parallel conflicts
   */
  private RerunResult executeRerunAttempts(List<FeatureWithLines> failedFeatures,
      TestaraCucumberEngineExecutionContext originalContext) {

    int totalScenarios = failedFeatures.stream().map(fail -> fail.lines().size()).reduce(0, Integer::sum);
    AtomicInteger passedScenarios = new AtomicInteger(0);
    AtomicInteger failedScenarios = new AtomicInteger(totalScenarios);

    int maxRetries = options.maxRetryFailedScenarios();
    log.debug(
        "Starting deferred rerun with max {} retry attempts (parallel strategy: attempts 1-{}, sequential: attempt {})",
        maxRetries,
        maxRetries > 1 ? maxRetries - 1 : 1,
        maxRetries);

    // Get the feature resolver from the original context
    TestaraFeatureResolver featureResolver = getFeatureResolver(originalContext);

    // Execute rerun attempts
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      boolean isLastAttempt = (attempt == maxRetries);
      String executionMode = isLastAttempt ? "sequential" : "parallel";

      log.debug("Starting deferred rerun attempt #{} ({} execution mode)", attempt, executionMode);

      // Create separate CucumberExecutionContext for this attempt to guarantee separate report file
      CucumberExecutionContext rerunContext = createRerunCucumberContext(attempt, originalContext);

      try {
        // Start the rerun context
        rerunContext.startTestRun();
        rerunContext.runBeforeAllHooks();

        // Execute failed features with hybrid parallel/sequential strategy
        List<FeatureWithLines> stillFailing = executeFailedFeaturesWithHybridStrategy(failedFeatures,
            originalContext,
            rerunContext,
            featureResolver,
            attempt,
            isLastAttempt,
            passedScenarios,
            failedScenarios);

        // Finish the rerun context - this will write the cucumber-N.json file
        rerunContext.runAfterAllHooks();
        rerunContext.finishTestRun();

        // Update failed features for next attempt
        failedFeatures = stillFailing;

        log.debug("Deferred rerun attempt #{} ({}) completed. {} scenarios still failing",
            attempt,
            executionMode,
            failedFeatures.size());

        // If no scenarios are still failing, we're done
        if (failedFeatures.isEmpty()) {
          log.info("All scenarios passed after {} attempts (final mode: {})", attempt, executionMode);
          break;
        }

      } catch (Exception e) {
        log.error("Error during deferred rerun attempt #{} ({}) : {}", attempt, executionMode, e.getMessage());

        // Still finish the context to ensure report is written
        try {
          rerunContext.runAfterAllHooks();
          rerunContext.finishTestRun();
          log.info("Finished rerun context for attempt #{} ({}) despite execution error", attempt, executionMode);
        } catch (Exception finishError) {
          log.warn("Error finishing rerun context for attempt #{} ({})", attempt, executionMode, finishError);
        }
      }
    }

    return new RerunResult(totalScenarios,
        passedScenarios.get(),
        String.format("Deferred rerun completed: %d scenarios executed, %d passed, %d failed",
            totalScenarios,
            passedScenarios.get(),
            failedScenarios.get()));
  }

  /**
   * Execute failed features with hybrid parallel/sequential strategy
   * - Early attempts: Run in parallel for speed
   * - Final attempt: Run sequentially to handle flaky tests caused by parallel conflicts
   */
  private List<FeatureWithLines> executeFailedFeaturesWithHybridStrategy(List<FeatureWithLines> failedFeatures,
      TestaraCucumberEngineExecutionContext originalContext,
      CucumberExecutionContext rerunContext,
      TestaraFeatureResolver featureResolver,
      int attempt,
      boolean isLastAttempt,
      AtomicInteger passedScenarios,
      AtomicInteger failedScenarios) {

    // Parse all failed scenarios and organize them by priority
    List<ScenarioRerunInfo> allFailedScenarios = parseAndOrganizeFailedScenarios(failedFeatures, featureResolver);

    // Sort scenarios by priority (critical scenarios first)
    allFailedScenarios.sort(new ScenarioRerunComparator());

    String executionMode = isLastAttempt ? "sequential" : "parallel";
    log.debug("Executing {} failed scenarios in {} mode for attempt #{}",
        allFailedScenarios.size(),
        executionMode,
        attempt);

    // Execute scenarios based on hybrid strategy
    List<ScenarioRerunInfo> stillFailingScenarios;

    if (isLastAttempt) {
      // Final attempt: Force sequential execution to handle potential parallel conflicts
      log.info("Final attempt: forcing sequential execution to handle potential parallel conflicts");
      stillFailingScenarios = executeSequentially(allFailedScenarios,
          originalContext,
          rerunContext,
          attempt,
          passedScenarios,
          failedScenarios);
    } else {
      // Early attempts: Use parallel execution for speed (respects parallel configuration)
      stillFailingScenarios = executeScenariosInParallel(allFailedScenarios,
          originalContext,
          rerunContext,
          attempt,
          passedScenarios,
          failedScenarios);
    }

    // Convert back to FeatureWithLines format
    return groupScenariosByFeature(stillFailingScenarios);
  }

  /**
   * Parse failed features and create ScenarioRerunInfo objects for better organization
   */
  private List<ScenarioRerunInfo> parseAndOrganizeFailedScenarios(List<FeatureWithLines> failedFeatures,
      TestaraFeatureResolver featureResolver) {
    List<ScenarioRerunInfo> scenarios = new ArrayList<>();

    for (FeatureWithLines failedFeature : failedFeatures) {
      try {
        // Parse the feature to get the actual Feature object
        List<Feature> features = featureResolver.parseFeatures(FeatureWithLines.parse(failedFeature.toString()));

        if (features.isEmpty()) {
          log.warn("No features found for {}", failedFeature.uri());
          continue;
        }

        Feature feature = features.get(0);

        // Create ScenarioRerunInfo for each failed scenario
        for (Integer line : failedFeature.lines()) {
          Pickle pickle = findPickleAtLine(feature, line);
          if (pickle != null) {
            TestaraNodeDescriptor.PickleDescriptor descriptor = createPickleDescriptor(pickle, feature);
            scenarios.add(new ScenarioRerunInfo(failedFeature, feature, pickle, descriptor, line));
          } else {
            log.warn("No pickle found at line {} in feature {}", line, failedFeature.uri());
          }
        }
      } catch (Exception e) {
        log.error("Error parsing feature {}", failedFeature.uri(), e);
      }
    }

    return scenarios;
  }

  /**
   * Execute scenarios in parallel while respecting critical scenario promotion and exclusive resources
   * Enhanced to process priority groups in order while allowing parallel execution within each group
   */
  private List<ScenarioRerunInfo> executeScenariosInParallel(List<ScenarioRerunInfo> scenarios,
      TestaraCucumberEngineExecutionContext originalContext,
      CucumberExecutionContext rerunContext,
      int attempt,
      AtomicInteger passedScenarios,
      AtomicInteger failedScenarios) {

    // Determine if parallel execution is enabled
    boolean parallelEnabled = options.isParallelExecutionEnabled();

    if (!parallelEnabled) {
      log.info("Parallel execution disabled, running scenarios sequentially");
      return executeSequentially(scenarios, originalContext, rerunContext, attempt, passedScenarios, failedScenarios);
    }

    // Group scenarios by priority level and exclusive resources
    List<List<ScenarioRerunInfo>> executionGroups = groupScenariosByExclusiveResources(scenarios);

    // Sort execution groups by priority (critical scenarios first)
    executionGroups.sort((group, otherGroup) -> {
      Integer priority = getGroupPriority(group);
      Integer otherPriority = getGroupPriority(otherGroup);
      return priority.compareTo(otherPriority);
    });

    List<ScenarioRerunInfo> stillFailing = new ArrayList<>();

    // Execute each priority group in order
    for (int groupIndex = 0; groupIndex < executionGroups.size(); groupIndex++) {
      List<ScenarioRerunInfo> group = executionGroups.get(groupIndex);
      Integer groupPriority = getGroupPriority(group);

      log.info("Executing priority group {} of {} (priority=@Order={}) with {} scenarios",
          groupIndex + 1,
          executionGroups.size(),
          groupPriority == Integer.MAX_VALUE ? "none" : groupPriority,
          group.size());

      if (group.size() == 1 && !group.get(0).descriptor.getExclusiveResources().isEmpty()) {
        // Single scenario with exclusive resources - must run alone
        ScenarioRerunInfo scenario = group.get(0);
        log.debug("Executing exclusive scenario: {}", scenario.pickle.getName());

        try {
          ScenarioRerunResult result = executeScenarioRerun(scenario, originalContext, rerunContext, attempt);
          if (result.isSuccess()) {
            passedScenarios.incrementAndGet();
            failedScenarios.decrementAndGet();
          } else {
            stillFailing.add(scenario);
          }
        } catch (Exception e) {
          stillFailing.add(scenario);
        }
      } else {
        // Multiple scenarios or single scenario without exclusive resources - can run in parallel
        log.debug("Executing {} scenarios in parallel for priority group", group.size());

        List<CompletableFuture<ScenarioRerunResult>> futures = group.stream()
            .map(scenario -> CompletableFuture.supplyAsync(() -> executeScenarioRerun(scenario,
                originalContext,
                rerunContext,
                attempt), scenarioExecutor))  // ← Use bounded scenario executor
            .collect(Collectors.toList());

        // Wait for all scenarios in this priority group to complete before moving to next group
        for (int i = 0; i < futures.size(); i++) {
          ScenarioRerunResult result;
          try {
            result = futures.get(i).get();
            if (result.isSuccess()) {
              passedScenarios.incrementAndGet();
              failedScenarios.decrementAndGet();
            } else {
              stillFailing.add(group.get(i));
            }
          } catch (Exception ignored) {
            stillFailing.add(group.get(i));
          }
        }
      }

      log.debug("Completed priority group {} with {} scenarios still failing", groupIndex + 1, stillFailing.size());
    }

    return stillFailing;
  }

  /**
   * Get the priority of a group based on the minimum @Order value in the group
   */
  private Integer getGroupPriority(List<ScenarioRerunInfo> group) {
    return group.stream()
        .map(scenario -> TestDescriptorOrderUtils.getOrder(scenario.descriptor))
        .filter(Objects::nonNull)
        .min(Integer::compareTo)
        .orElse(Integer.MAX_VALUE);
  }

  /**
   * Execute scenarios sequentially when parallel execution is disabled
   */
  private List<ScenarioRerunInfo> executeSequentially(List<ScenarioRerunInfo> scenarios,
      TestaraCucumberEngineExecutionContext originalContext,
      CucumberExecutionContext rerunContext,
      int attempt,
      AtomicInteger passedScenarios,
      AtomicInteger failedScenarios) {

    List<ScenarioRerunInfo> stillFailing = new ArrayList<>();

    for (ScenarioRerunInfo scenario : scenarios) {
      try {
        ScenarioRerunResult result = executeScenarioRerun(scenario, originalContext, rerunContext, attempt);
        if (result.isSuccess()) {
          passedScenarios.incrementAndGet();
          failedScenarios.decrementAndGet();
        } else {
          stillFailing.add(scenario);
        }
      } catch (Exception ignored) {
        stillFailing.add(scenario);
      }
    }

    return stillFailing;
  }

  /**
   * Group scenarios by exclusive resources to avoid parallel execution conflicts
   * Enhanced to group scenarios by priority level and exclusive resources properly
   */
  private List<List<ScenarioRerunInfo>> groupScenariosByExclusiveResources(List<ScenarioRerunInfo> scenarios) {
    List<List<ScenarioRerunInfo>> groups = new ArrayList<>();

    // Group scenarios by priority level first
    Map<Integer, List<ScenarioRerunInfo>> priorityGroups =
        scenarios.stream().collect(Collectors.groupingBy(scenario -> {
          Integer order = TestDescriptorOrderUtils.getOrder(scenario.descriptor);
          return order != null ? order : Integer.MAX_VALUE;
        }));

    // Process each priority group
    for (Map.Entry<Integer, List<ScenarioRerunInfo>> entry : priorityGroups.entrySet()) {
      Integer priority = entry.getKey();
      List<ScenarioRerunInfo> priorityScenarios = entry.getValue();

      log.debug("Processing priority group @Order={} with {} scenarios",
          priority == Integer.MAX_VALUE ? "none" : priority,
          priorityScenarios.size());

      // Separate scenarios with and without exclusive resources within this priority
      List<ScenarioRerunInfo> nonExclusiveScenarios = new ArrayList<>();
      List<ScenarioRerunInfo> exclusiveScenarios = new ArrayList<>();

      for (ScenarioRerunInfo scenario : priorityScenarios) {
        if (scenario.descriptor.getExclusiveResources().isEmpty()) {
          nonExclusiveScenarios.add(scenario);
        } else {
          exclusiveScenarios.add(scenario);
        }
      }

      // Add non-exclusive scenarios as a single group (can run in parallel)
      if (!nonExclusiveScenarios.isEmpty()) {
        groups.add(nonExclusiveScenarios);
        log.debug("Added parallel group for priority {} with {} non-exclusive scenarios",
            priority == Integer.MAX_VALUE ? "none" : priority,
            nonExclusiveScenarios.size());
      }

      // Add exclusive scenarios as individual groups (must run alone)
      for (ScenarioRerunInfo exclusiveScenario : exclusiveScenarios) {
        groups.add(List.of(exclusiveScenario));
        log.debug("Added exclusive group for priority {} with scenario: {}",
            priority == Integer.MAX_VALUE ? "none" : priority,
            exclusiveScenario.pickle.getName());
      }
    }

    log.debug("Created {} execution groups from {} scenarios for parallel execution", groups.size(), scenarios.size());

    return groups;
  }

  /**
   * Capture the TestContext from the current thread for later propagation.
   * This is called from the main/original test thread where the context is initialized.
   */
  private void captureTestContext() {
    try {
      this.capturedTestContext = TestFramework.context();
      log.debug("Captured TestContext for rerun thread propagation");
    } catch (IllegalStateException e) {
      log.warn("Failed to capture TestContext: {}. Rerun threads may not have proper context.", e.getMessage());
    }
  }

  /**
   * Ensure the TestFramework context is available in the current thread.
   * If not available, initialize it from the captured context.
   * This handles the case where thread pool threads were created before framework initialization.
   */
  private void ensureFrameworkContextAvailable() {
    try {
      // Check if context is already available in this thread
      TestFramework.context();
      log.trace("TestFramework context already available in thread: {}", Thread.currentThread().getName());
    } catch (IllegalStateException e) {
      // Context not available - initialize from captured context
      if (capturedTestContext != null) {
        TestFramework.initialize(capturedTestContext);
        log.debug("Propagated TestFramework context to thread: {}", Thread.currentThread().getName());
      } else {
        log.warn("No captured TestContext available to propagate to thread: {}", Thread.currentThread().getName());
      }
    }
  }

  /**
   * Cleanup executor resources
   * Called when DeferredRerunManager is no longer needed
   */
  public void shutdown() {
    log.trace("Shutting down deferred rerun executors");

    // Shutdown scenario executor first (may have running tasks)
    boolean scenarioShutdown =
        ExecutorFactory.safeShutdown(scenarioExecutor, 10,  // Give more time for running scenarios to complete
            "deferred-rerun-scenario");
    if (scenarioShutdown) {
      log.trace("All deferred rerun executors shut down successfully");
    } else {
      log.warn("Some deferred rerun executors did not terminate gracefully");
    }
  }

  /**
   * Execute a single scenario rerun and return the result
   */
  private ScenarioRerunResult executeScenarioRerun(ScenarioRerunInfo scenarioInfo,
      TestaraCucumberEngineExecutionContext originalContext,
      CucumberExecutionContext rerunContext,
      int attempt) {

    try {
      // CRITICAL: Ensure TestFramework context is available in this thread
      // Thread pool threads may have been created before framework initialization,
      // so InheritableThreadLocal won't have propagated the context
      ensureFrameworkContextAvailable();

      // Set up feature context in the rerun context
      rerunContext.beforeFeature(scenarioInfo.feature);
      TestaraExtensionContext extensionContext =
          new TestaraExtensionContext(scenarioInfo.descriptor, originalContext.getOptions());
      List<TestaraExtension> extensions = originalContext.getExtensions();


      try {
        for (TestaraExtension extension : extensions) {
          try {
            log.trace("Running beforeEach for {}", extension.getClass().getSimpleName());
            extension.beforeEach(extensionContext);
          } catch (Throwable t) {
            log.trace("beforeEach failed for {}", extension.getClass().getSimpleName(), t);
          }
        }

        // Execute the scenario using the separate rerun context
        rerunContext.runTestCase((runner) -> {
          TestaraTestCaseResultObserver observer = TestaraTestCaseResultObserver.observe(runner.getBus(),
              scenarioInfo.descriptor,
              originalContext.getListener(),
              originalContext.getOptions().stepNotifications());

          observer.isRerun(true);

          try {
            runner.runPickle(scenarioInfo.pickle);
            observer.assertTestCasePassed();
          } catch (Exception error) {
            log.error("Error re-running scenario {} : {}",
                scenarioInfo.descriptor.getDisplayName(),
                error.getMessage());
            observer.close();
            throw error;
          }
          observer.close();
        });
      } finally {
        for (TestaraExtension extension : extensions) {
          try {
            log.trace("Running afterEach for {}", extension.getClass().getSimpleName());
            extension.afterEach(extensionContext);
          } catch (Throwable t) {
            log.trace("afterEach failed for {}", extension.getClass().getSimpleName(), t);
          }
        }
      }

      log.debug("Deferred rerun attempt #{} for scenario {} - SUCCESS", attempt, scenarioInfo.pickle.getName());
      return new ScenarioRerunResult(true);
    } catch (Exception e) {
      log.debug("Deferred rerun attempt #{} for scenario {} - FAILED: {}",
          attempt,
          scenarioInfo.pickle.getName(),
          e.getMessage());
      return new ScenarioRerunResult(false);
    }
  }

  /**
   * Group scenarios back into FeatureWithLines format
   */
  private List<FeatureWithLines> groupScenariosByFeature(List<ScenarioRerunInfo> scenarios) {
    return scenarios.stream()
        .collect(Collectors.groupingBy(scenario -> scenario.failedFeature.uri(),
            Collectors.mapping(scenario -> scenario.line, Collectors.toList())))
        .entrySet()
        .stream()
        .map(entry -> FeatureWithLines.create(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  /**
   * Create a separate CucumberExecutionContext for rerun with different report path
   * This guarantees that each rerun attempt generates its own cucumber-N.json file
   */
  private CucumberExecutionContext createRerunCucumberContext(int attempt,
      TestaraCucumberEngineExecutionContext originalContext) {

    String reportName = options.reportName();
    // Calculate the report path for this rerun attempt
    String rerunReportPath = options.reportPath() + FilenameUtils.getBaseName(reportName) + "-" + attempt + ".json";

    log.debug("Creating rerun context #{} with report path: {}", attempt, rerunReportPath);

    // Use the same method as the original context but with different report path
    return originalContext.createCucumberExecutionContextForRerun(rerunReportPath);
  }

  /**
   * Get the feature resolver from the execution context
   */
  private TestaraFeatureResolver getFeatureResolver(TestaraCucumberEngineExecutionContext context) {
    // Create a minimal engine descriptor for the feature resolver
    TestaraCucumberEngineDescriptor engineDescriptor = new TestaraCucumberEngineDescriptor(forEngine("testara-cucumber"));

    return TestaraFeatureResolver.create(context.getOptions().getConfigurationParameters(),
        engineDescriptor,
        packageName -> true);
  }

  /**
   * Find the pickle (scenario) at a specific line in a feature
   */
  private Pickle findPickleAtLine(Feature feature, int line) {
    return feature.getPickles()
        .stream()
        .filter(pickle -> pickle.getLocation().getLine() == line)
        .findFirst()
        .orElse(null);
  }

  /**
   * Create a PickleDescriptor for a scenario following the same pattern as TestaraFeatureResolver
   */
  private TestaraNodeDescriptor.PickleDescriptor createPickleDescriptor(Pickle pickle, Feature feature) {
    TestaraFeatureOrigin origin = TestaraFeatureOrigin.fromUri(feature.getUri());

    // Create a temporary parent UniqueId for the scenario
    UniqueId parentId =
        forEngine("testara-cucumber").append("feature", feature.getUri().toString());

    return new TestaraNodeDescriptor.PickleDescriptor(options,
        origin,
        origin.scenarioSegment(parentId, createNodeFromPickle(pickle)),
        pickle.getName(),
        origin.nodeSource(createNodeFromPickle(pickle)),
        pickle);
  }

  /**
   * Create a Node representation from a Pickle for compatibility with TestaraFeatureOrigin methods
   */
  private Node createNodeFromPickle(Pickle pickle) {
    // Create a minimal Node implementation that provides the required methods
    return new Node() {
      @Override
      public Location getLocation() {
        return pickle.getLocation();
      }

      @Override
      public Optional<String> getKeyword() {
        return Optional.of("Scenario"); // Default keyword for scenarios
      }

      @Override
      public Optional<String> getName() {
        return Optional.of(pickle.getName());
      }

      @Override
      public Optional<Node> getParent() {
        return Optional.empty(); // No parent needed for this minimal implementation
      }
    };
  }

  /**
   * Check if deferred rerun should be executed based on strategy
   */
  public boolean shouldExecuteDeferred() {
    RerunStrategy strategy = options.getRerunStrategy();
    return strategy == RerunStrategy.DEFERRED || strategy == RerunStrategy.COMBINE;
  }

  /**
   * Get the maximum number of retry attempts for deferred reruns
   */
  public int getMaxRetryAttempts() {
    return options.maxRetryFailedScenarios();
  }

  /**
   * Get the list of rerun report files that have been created
   * This can be used by the main execution context to ensure all files are written before generating reports
   */
  public List<File> getRerunReportFiles() {
    return new ArrayList<>(rerunReportFiles);
  }

  /**
   * Check if deferred rerun was actually executed
   * This helps avoid unnecessary waiting when no reruns occurred
   */
  public boolean wasDeferredRerunExecuted() {
    return deferredRerunExecuted;
  }

  /**
   * Result of a deferred rerun execution
   */
  public static class RerunResult {
    private final int totalScenarios;
    private final int passedScenarios;
    private final String message;

    public RerunResult(int totalScenarios, int passedScenarios, String message) {
      this.totalScenarios = totalScenarios;
      this.passedScenarios = passedScenarios;
      this.message = message;
    }

    public int getTotalScenarios() {
      return totalScenarios;
    }

    public int getPassedScenarios() {
      return passedScenarios;
    }

    public int getFailedScenarios() {
      return totalScenarios - passedScenarios;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public String toString() {
      return String.format("RerunResult{total=%d, passed=%d, failed=%d, message='%s'}",
          totalScenarios,
          passedScenarios,
          getFailedScenarios(),
          message);
    }
  }


  /**
   * Information about a scenario to be rerun
   */
  private static class ScenarioRerunInfo {
    final FeatureWithLines failedFeature;
    final Feature feature;
    final Pickle pickle;
    final TestaraNodeDescriptor.PickleDescriptor descriptor;
    final Integer line;

    ScenarioRerunInfo(FeatureWithLines failedFeature,
        Feature feature,
        Pickle pickle,
        TestaraNodeDescriptor.PickleDescriptor descriptor,
        Integer line) {
      this.failedFeature = failedFeature;
      this.feature = feature;
      this.pickle = pickle;
      this.descriptor = descriptor;
      this.line = line;
    }
  }


  /**
   * Comparator for sorting scenarios by critical scenario promotion rules
   */
  private static class ScenarioRerunComparator implements Comparator<ScenarioRerunInfo> {
    private final Comparator<TestDescriptor> nodeComparator;

    ScenarioRerunComparator() {
      this.nodeComparator =
          comparing(TestDescriptorOrderUtils::getOrder).thenComparing(TestDescriptorOrderUtils::getExclusiveResourceCount)
              .thenComparing(TestDescriptor::getDisplayName);
    }

    @Override
    public int compare(ScenarioRerunInfo info, ScenarioRerunInfo otherInfo) {
      // Use the same comparison logic as the main engine
      return nodeComparator.compare(info.descriptor, otherInfo.descriptor);
    }
  }


  /**
   * Result of a single scenario rerun execution
   */
  private static class ScenarioRerunResult {
    private final boolean success;

    ScenarioRerunResult(boolean success) {
      this.success = success;
    }

    public boolean isSuccess() {
      return success;
    }
  }
}