package io.github.ygrip.testara.engine.context;

import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.file.FileWaiter;
import io.github.ygrip.testara.engine.TestaraTestCaseResultObserver;
import io.github.ygrip.testara.engine.DeferredRerunManager;
import io.github.ygrip.testara.engine.descriptor.TestaraCucumberEngineDescriptor;
import io.github.ygrip.testara.engine.descriptor.TestaraNodeDescriptor;
import io.github.ygrip.testara.engine.extension.TestaraExtension;
import io.github.ygrip.testara.engine.extension.TestaraExtensionContext;
import io.github.ygrip.testara.engine.listener.FailedScenariosListener;
import io.github.ygrip.testara.engine.listener.ParallelExecutionTracer;
import io.github.ygrip.testara.engine.model.RerunStrategy;
import io.github.ygrip.testara.engine.option.TestaraConfigurationParameters;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import io.github.ygrip.testara.engine.support.ExceptionHandler;
import io.github.ygrip.testara.reporter.cucumber.CucumberSummaryReportGenerator;
import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.plugin.JsonFormatter;
import io.cucumber.core.plugin.PluginFactory;
import io.cucumber.core.plugin.Plugins;
import io.cucumber.core.runtime.BackendServiceLoader;
import io.cucumber.core.runtime.BackendSupplier;
import io.cucumber.core.runtime.CucumberExecutionContext;
import io.cucumber.core.runtime.ExitStatus;
import io.cucumber.core.runtime.ObjectFactoryServiceLoader;
import io.cucumber.core.runtime.ObjectFactorySupplier;
import io.cucumber.core.runtime.RunnerSupplier;
import io.cucumber.core.runtime.SingletonObjectFactorySupplier;
import io.cucumber.core.runtime.SingletonRunnerSupplier;
import io.cucumber.core.runtime.ThreadLocalObjectFactorySupplier;
import io.cucumber.core.runtime.ThreadLocalRunnerSupplier;
import io.cucumber.core.runtime.TimeServiceEventBus;
import io.cucumber.plugin.event.Status;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apiguardian.api.API;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.cucumber.core.runtime.SynchronizedEventBus.synchronize;

@Log4j2
@API(status = API.Status.STABLE)
public final class TestaraCucumberEngineExecutionContext implements EngineExecutionContext {
  @Getter
  private final TestaraCucumberEngineOptions options;
  private final List<File> reportFiles;
  private final EngineExecutionListener listener;
  // The real, originally-discovered descriptor tree for this run (the exact object graph produced
  // by TestaraCucumberTestEngine#discover). Kept so a deferred rerun can look up and reuse the SAME
  // PickleDescriptor instance that was actually discovered, instead of DeferredRerunManager having
  // to fabricate a brand new, disconnected descriptor with a merely-coincidentally-matching id.
  private final TestDescriptor rootTestDescriptor;
  private volatile Map<UniqueId, TestaraNodeDescriptor.PickleDescriptor> pickleDescriptorIndex;
  // Deferred rerun components
  private final DeferredRerunManager deferredRerunManager;
  @Getter
  private final List<TestaraExtension> extensions = new ArrayList<>();
  private CucumberExecutionContext context;
  private FailedScenariosListener failedScenariosListener;

  public TestaraCucumberEngineExecutionContext(ExecutionRequest request) {
    this.reportFiles = new ArrayList<>();
    this.listener = request.getEngineExecutionListener();
    this.rootTestDescriptor = request.getRootTestDescriptor();
    this.options = new TestaraCucumberEngineOptions(TestaraConfigurationParameters.merge(request.getConfigurationParameters()));

    // Initialize rerun components
    loadExtensions(options);
    this.deferredRerunManager = new DeferredRerunManager(options);
  }

  /**
   * Look up the real {@link TestaraNodeDescriptor.PickleDescriptor} that was actually discovered
   * for the given unique id, if any. Used by {@link DeferredRerunManager} so a deferred rerun
   * reports through the SAME descriptor object that JUnit Platform already knows about, rather than
   * a freshly fabricated one that only coincidentally shares the same {@link UniqueId} string --
   * the latter is what previously caused a failed-then-deferred-rerun scenario to surface as two
   * separate entries in an IDE's test tree instead of one.
   */
  public synchronized Optional<TestaraNodeDescriptor.PickleDescriptor> findPickleDescriptor(UniqueId uniqueId) {
    ensurePickleDescriptorIndex();
    return Optional.ofNullable(this.pickleDescriptorIndex.get(uniqueId));
  }

  private void ensurePickleDescriptorIndex() {
    if (this.pickleDescriptorIndex == null) {
      Map<UniqueId, TestaraNodeDescriptor.PickleDescriptor> index = new HashMap<>();
      indexPickleDescriptors(this.rootTestDescriptor, index);
      this.pickleDescriptorIndex = index;
    }
  }

  private static void indexPickleDescriptors(TestDescriptor descriptor,
      Map<UniqueId, TestaraNodeDescriptor.PickleDescriptor> index) {
    if (descriptor instanceof TestaraNodeDescriptor.PickleDescriptor) {
      index.put(descriptor.getUniqueId(), (TestaraNodeDescriptor.PickleDescriptor) descriptor);
    }
    for (TestDescriptor child : descriptor.getChildren()) {
      indexPickleDescriptors(child, index);
    }
  }

  private void loadExtensions(TestaraCucumberEngineOptions options) {
    //Load via ServiceLoader
    ServiceLoader.load(TestaraExtension.class).forEach(extensions::add);

    //Load via system property or config
    Optional<String> prop = options.getConfigurationParameters().get("testara.cucumber.extensions");
    if (prop.isPresent()) {
      String properties = prop.get();
      if (!properties.isBlank()) {
        Arrays.stream(properties.split(",")).map(String::trim).forEach(this::instantiateExtension);
      }
    }

    log.debug("✅ Loaded testara extensions: {}",
        extensions.stream().map(e -> e.getClass().getSimpleName()).collect(Collectors.joining(", ")));
  }

  private void instantiateExtension(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      if (TestaraExtension.class.isAssignableFrom(clazz)) {
        extensions.add((TestaraExtension) clazz.getDeclaredConstructor().newInstance());
      }
    } catch (Exception ignored) {
      log.warn("⚠️ Failed to load testara extension: {}", className);
    }
  }

  public EngineExecutionListener getListener() {
    return this.listener;
  }

  private CucumberExecutionContext createCucumberExecutionContext(String jsonReportPath) {
    Supplier<ClassLoader> classLoader = TestaraCucumberEngineExecutionContext.class::getClassLoader;
    ObjectFactoryServiceLoader objectFactoryServiceLoader = new ObjectFactoryServiceLoader(classLoader, options);
    EventBus bus = synchronize(new TimeServiceEventBus(Clock.systemUTC(), UUID::randomUUID));
    Plugins plugins = new Plugins(new PluginFactory(), options);
    ExitStatus exitStatus = new ExitStatus(options);
    plugins.addPlugin(exitStatus);
    try {
      if (jsonReportPath != null && !jsonReportPath.trim().isEmpty()) {
        File reportFile = new File(System.getProperty("user.dir") + jsonReportPath);
        Files.createDirectories(Paths.get(reportFile.getPath()).getParent());
        JsonFormatter jsonFormatter = new JsonFormatter(new BufferedOutputStream(new FileOutputStream(reportFile)));
        reportFiles.add(reportFile);
        plugins.addPlugin(jsonFormatter);
      }
    } catch (IOException exception) {
      log.warn("Unable to add json report plugin", exception);
    }

    String rerunPath = System.getProperty("user.dir") + "/target/rerun/" + options.rerunFileName();
    failedScenariosListener = FailedScenariosListener.reportTo(rerunPath);
    plugins.addPlugin(failedScenariosListener);

    RunnerSupplier runnerSupplier;
    if (options.isParallelExecutionEnabled()) {
      plugins.setSerialEventBusOnEventListenerPlugins(bus);
      ObjectFactorySupplier objectFactorySupplier = new ThreadLocalObjectFactorySupplier(objectFactoryServiceLoader);
      BackendSupplier backendSupplier = new BackendServiceLoader(classLoader, objectFactorySupplier);
      runnerSupplier = new ThreadLocalRunnerSupplier(options, bus, backendSupplier, objectFactorySupplier);
    } else {
      plugins.setEventBusOnEventListenerPlugins(bus);
      ObjectFactorySupplier objectFactorySupplier = new SingletonObjectFactorySupplier(objectFactoryServiceLoader);
      BackendSupplier backendSupplier = new BackendServiceLoader(classLoader, objectFactorySupplier);
      runnerSupplier = new SingletonRunnerSupplier(options, bus, backendSupplier, objectFactorySupplier);
    }
    return new CucumberExecutionContext(bus, exitStatus, runnerSupplier);
  }

  /**
   * Create a separate CucumberExecutionContext for rerun with different report path
   * This guarantees that each rerun attempt generates its own cucumber-N.json file
   */
  public CucumberExecutionContext createCucumberExecutionContextForRerun(String jsonReportPath) {
    return createCucumberExecutionContext(jsonReportPath);
  }

  public void startTestRun() {
    log.debug("Starting test run");
    this.context = this.createCucumberExecutionContext(options.reportPath() + options.reportName());

    // Enable parallel execution tracer if configured
    if (options.getConfigurationParameters().getBoolean("cucumber.execution.parallel.trace.enabled").orElse(false)) {
      ParallelExecutionTracer.enable();
    }

    // Call extension beforeAll hooks
    TestaraExtensionContext extensionContext =
        new TestaraExtensionContext(new TestaraCucumberEngineDescriptor(UniqueId.forEngine("testara-cucumber"),
            options,
            null), options);
    for (TestaraExtension extension : extensions) {
      try {
        log.trace("Running beforeAll for {}", extension.getClass().getSimpleName());
        extension.beforeAll(extensionContext);
      } catch (Throwable t) {
        log.warn("beforeAll failed for {}: {}", extension.getClass().getSimpleName(), t.getMessage());
      }
    }

    this.context.startTestRun();
  }

  public void runBeforeAllHooks() {
    log.debug("Running before all hooks");
    this.context.runBeforeAllHooks();
  }

  public void beforeFeature(Feature feature) {
    this.context.beforeFeature(feature);
  }

  public void runTestCase(Pickle pickle, TestaraNodeDescriptor descriptor) {
    // Check for thread interruption before starting test case
    if (Thread.currentThread().isInterrupted()) {
      log.debug("Thread interrupted, skipping test case: {}", pickle.getName());
      ParallelExecutionTracer.scenarioFinished(pickle.getName(), false);
      return;
    }

    // Trace scenario start
    ParallelExecutionTracer.scenarioStarted(pickle.getName(), descriptor);

    try {
      this.context.runTestCase((runner) -> {
        // Check for interruption again before running
        if (Thread.currentThread().isInterrupted()) {
          log.debug("Thread interrupted before running pickle: {}", pickle.getName());
          return;
        }

        // IMMEDIATE and COMBINE's immediate phase ALWAYS attempt an immediate retry that supersedes
        // this original attempt whenever it fails -- that certainty is what lets reportFinalOutcome
        // suppress a failing step's real result here (shown instead by whichever attempt turns out
        // to be the actual last one). DEFERRED's original attempt has no such immediate retry within
        // this same call, so it always reports its own real, live result -- the later deferred retry
        // (if any) reports its own outcome separately, through the same descriptor, at the very end
        // of the run (see finishTestRun below): both reports reach the listener for the same
        // UniqueId, which is expected and accepted, not something this engine tries to merge into one.
        RerunStrategy rerunStrategy = options.getRerunStrategy();
        TestaraTestCaseResultObserver observer =
            TestaraTestCaseResultObserver.observe(runner.getBus(), descriptor, listener, options.stepNotifications());
        if (options.isRerunEnabled() && (rerunStrategy == RerunStrategy.IMMEDIATE || rerunStrategy == RerunStrategy.COMBINE)) {
          observer.reportFinalOutcome(false);
        }
        try {
          log.debug("Executing test case {}", pickle.getName());
          runner.runPickle(pickle);
          log.debug("Finished test case {}", pickle.getName());
          observer.assertTestCasePassed();
          observer.finish();

          // Trace scenario finish (passed)
          ParallelExecutionTracer.scenarioFinished(pickle.getName(), true);

        } catch (Throwable exception) {
          // Check if this is a shutdown/interruption exception
          if (isShutdownException(exception)) {
            log.debug("Test case cancelled due to shutdown: {}", pickle.getName());
            try {
              observer.finish();
            } catch (Exception ignored) {
            }
            ParallelExecutionTracer.scenarioFinished(pickle.getName(), false);
            return;
          }

          // This attempt's own execution is over either way; unhook from the shared event bus now
          // (every step event has already been reported live, as it happened).
          try {
            observer.finish();
          } catch (Exception ignored) {
            // Ignore finish errors
          }

          Status status = observer.getResult().getStatus();

          if (options.isRerunEnabled() && status.is(Status.FAILED)) {
            if (rerunStrategy == RerunStrategy.IMMEDIATE) {
              // Immediate rerun - existing behavior. This observer is in live mode (see
              // reportFinalOutcome(false) above), so its own steps were already reported correctly,
              // live, as the retry chain ran.
              try {
                status = rerunFailedTestCase(1, status, pickle, descriptor);
              } catch (Throwable error) {
                if (!isShutdownException(error)) {
                  exception.addSuppressed(error);
                }
              }
            } else if (rerunStrategy == RerunStrategy.DEFERRED) {
              // Deferred rerun: this scenario's own failure is left to propagate normally below, so
              // JUnit Platform reports THIS attempt's own failed result through the descriptor as
              // usual. FailedScenariosListener (a Cucumber plugin on the shared bus) already tracks
              // this failure automatically; the actual retry runs later, at the very end of the run,
              // from finishTestRun -- through the same, real, originally-discovered descriptor (see
              // findPickleDescriptor) -- and reports its own outcome separately when it does.
              log.debug("Scenario {} failed, recorded for deferred rerun", pickle.getName());
            } else if (rerunStrategy == RerunStrategy.COMBINE) {
              // Combined rerun - try immediate first. Like IMMEDIATE above, this observer is in live
              // mode: its own steps already reported live, correctly, during the immediate retry
              // attempted just below. If it still fails after that immediate phase, it is handled
              // exactly like DEFERRED above: left to propagate normally, tracked automatically by
              // FailedScenariosListener, and retried later from finishTestRun.
              try {
                status = rerunFailedTestCase(1, status, pickle, descriptor);
                if (status.is(Status.FAILED)) {
                  log.debug("Scenario {} failed after immediate rerun, recorded for deferred rerun", pickle.getName());
                }
              } catch (Throwable error) {
                if (!isShutdownException(error)) {
                  exception.addSuppressed(error);
                }
                log.debug("Scenario {} failed during immediate rerun, recorded for deferred rerun", pickle.getName());
              }
            }
            // For RerunStrategy.NONE, do nothing - scenario fails normally
          }

          if (!status.isOk()) {
            // Trace scenario finish (failed). The failure is always propagated so JUnit Platform
            // marks the scenario as failed, regardless of step-notification mode. Any live per-step
            // reporting has already fired synchronously during runner.runPickle above.
            ParallelExecutionTracer.scenarioFinished(pickle.getName(), false);
            ExceptionHandler.trimStackTrace(exception);
            throw exception;
          } else {
            ParallelExecutionTracer.scenarioFinished(pickle.getName(), true);
          }
        }
      });
    } catch (RuntimeException e) {
      // Handle shutdown exceptions gracefully
      if (isShutdownException(e)) {
        log.debug("Test case execution cancelled due to shutdown: {}", pickle.getName());
        ParallelExecutionTracer.scenarioFinished(pickle.getName(), false);
      } else {
        throw e;
      }
    }
  }

  /**
   * Check if an exception is related to shutdown/interruption.
   * These exceptions should be handled gracefully during test stop.
   *
   * @param t The throwable to check
   * @return true if this is a shutdown-related exception
   */
  private boolean isShutdownException(Throwable t) {
    if (t == null) {
      return false;
    }

    // Direct shutdown-related exceptions
    if (t instanceof InterruptedException) {
      return true;
    }

    // Check class name for NIO channel interruption
    String className = t.getClass().getName();
    if (className.contains("ClosedByInterruptException") ||
        className.contains("ClosedChannelException") ||
        className.contains("AsynchronousCloseException") ||
        className.contains("RejectedExecutionException") ||
        className.contains("CancellationException")) {
      return true;
    }

    // Check message for common shutdown indicators
    String message = t.getMessage();
    if (message != null) {
      String lowerMessage = message.toLowerCase();
      if (lowerMessage.contains("executor is closed") ||
          lowerMessage.contains("shutdown") ||
          lowerMessage.contains("interrupted") ||
          lowerMessage.contains("rejected")) {
        return true;
      }
    }

    // Check cause recursively
    Throwable cause = t.getCause();
    if (cause != null && cause != t) {
      return isShutdownException(cause);
    }

    return false;
  }

  Status rerunFailedTestCase(int attempt, Status previousStatus, Pickle pickle, TestaraNodeDescriptor descriptor)
      throws Throwable {
    if (options.isRerunEnabled() && previousStatus.is(Status.FAILED) && attempt <= options.maxRetryFailedScenarios()) {
      AtomicReference<Status> currentStatus = new AtomicReference<>(previousStatus);
      AtomicReference<Throwable> throwable = new AtomicReference<>();
      this.context.runTestCase((runner) -> {
        // Live mode (not buffered): this attempt's own fate is already certain in advance, exactly
        // like the immediate-retry construction in runTestCase above -- attempt == maxRetry is known
        // before running, and an early PASS is always shown live regardless (the recursive retry
        // chain below stops as soon as one attempt passes, so an early-passing attempt is never
        // actually superseded even though it is not the "last configured" one).
        TestaraTestCaseResultObserver observer =
            TestaraTestCaseResultObserver.observe(runner.getBus(), descriptor, listener, options.stepNotifications())
                .reportFinalOutcome(attempt == options.maxRetryFailedScenarios());
        try {
          log.debug("Rerun #{} for test case {}", attempt, pickle.getName());
          runner.runPickle(pickle);
          log.debug("Finished rerun #{} test case {}", attempt, pickle.getName());
          currentStatus.set(observer.getResult().getStatus());
          throwable.set(observer.getResult().getError());

        } catch (Throwable error) {
          currentStatus.set(Status.FAILED);
          throwable.set(ExceptionHandler.trimStackTrace(error));
        }
        try {
          observer.finish();
        } catch (Exception ignored) {
          // Ignore observer finish errors
        }
      });

      if (currentStatus.get().is(Status.FAILED) && attempt < options.maxRetryFailedScenarios()) {
        currentStatus.set(rerunFailedTestCase(attempt + 1, currentStatus.get(), pickle, descriptor));
      } else if (!currentStatus.get().is(Status.PASSED) && attempt >= options.maxRetryFailedScenarios()) {
        throw throwable.get();
      }
      return currentStatus.get();
    } else {
      return previousStatus;
    }
  }

  void generateReport() {
    try {
      int effectiveTimeout = options.fileAwaitTimeoutMillis();
      log.debug("Waiting for {} report files (timeout: {}ms)", reportFiles.size(), effectiveTimeout);
      boolean reportFilesCreated =
          FileWaiter.waitUntilAllExist(reportFiles.stream().map(File::toPath).collect(Collectors.toList()),
              Duration.ofMillis(effectiveTimeout),
              Duration.ofMillis(200));
      if (reportFilesCreated) {
        log.trace("All report files are ready for merging");
      } else {
        throw new Exception("Timeout while waiting for report files");
      }

      log.debug("Starting report merge process");
      String reportDir = options.reportPath().trim();
      if (!reportDir.endsWith("/")) {
        reportDir += "/";
      }
      String mergedReportPath =
          CucumberSummaryReportGenerator.fromLocation(reportDir).mergeReportAs(options.reportName());
      log.info("Merged report created: {}", mergedReportPath);

      boolean merged =
          FileWaiter.waitUntilAllExist(Collections.singletonList(FileHelper.openFile(mergedReportPath).toPath()),
              Duration.ofMillis(effectiveTimeout),
              Duration.ofMillis(200));

      if (merged) {
        if (options.isGenerateTestaraCustomReportEnabled()) {
          log.debug("Generating custom summary reports");
          try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader("pom.xml"));
            String applicationName = model.getName();
            CucumberSummaryReportGenerator.fromLocation(options.reportPath())
                .withOutputFileName("summary")
                .withReportName(applicationName)
                .generateReport(false);
            log.debug("Custom summary report generated");

            CucumberSummaryReportGenerator.fromLocation(options.reportPath())
                .aggregate("aggregate-summary.json", false);
            log.debug("Aggregate summary created");
          } catch (Exception e) {
            log.warn("Failed to generate custom reports: {}", e.getMessage());
          }
        }
        log.trace("Report generation completed successfully");
      } else {
        throw new Exception("Timeout while waiting report files to be merged");
      }
    } catch (Exception e) {
      log.error("Failed to merge cucumber report: {}", e.getMessage(), e);
    }
  }

  public void runAfterAllHooks() {
    log.debug("Running after all hooks");
    this.context.runAfterAllHooks();
  }

  public void finishTestRun() {
    // Print parallel execution summary before finishing
    ParallelExecutionTracer.printExecutionSummary();

    boolean isVirtualThreadEnabled = options.getConfigurationParameters()
        .getBoolean("cucumber.execution.parallel.virtual-thread.enabled")
        .orElse(false);

    // Every scenario's own execute() call has already returned to JUnit Platform by the time we get
    // here (no scenario ever blocks its own execute() on anything). Failed scenarios were tracked
    // automatically along the way by FailedScenariosListener (a Cucumber plugin on the shared bus);
    // the deferred-rerun batch for DEFERRED/COMBINE strategies is kicked off here, once, at the very
    // end of the run.
    if (deferredRerunManager.shouldExecuteDeferred()) {
      DeferredRerunManager.RerunResult rerunResult;
      try {
        rerunResult = deferredRerunManager.executeDeferred(getFailedFeaturesFromListener(), this).get();
      } catch (Exception e) {
        // Safety fallback - should never happen as the batch catches all exceptions
        log.error("Unexpected error getting deferred rerun result: {}",
            e.getMessage(),
            ExceptionHandler.trimStackTrace(e));
        int totalScenarios =
            getFailedFeaturesFromListener().stream().map(fail -> fail.lines().size()).reduce(0, Integer::sum);
        rerunResult =
            new DeferredRerunManager.RerunResult(totalScenarios, 0, "Deferred rerun interrupted: " + e.getMessage());
      }

      if (rerunResult.getTotalScenarios() > 0) {
        printRerunSummary(rerunResult);
      } else {
        log.debug("No failed scenarios found for deferred rerun");
      }
    }

    log.debug("Cleaning up old report files before report generation");
    try {
      FileHelper.deleteFile(options.reportPath() + File.separator + "aggregate-summary.json");
    } catch (Exception e) {
      log.warn("Failed to delete old aggregate-summary.json: {}", e.getMessage());
    }
    try {
      FileHelper.deleteFile(options.reportPath() + File.separator + "summary.html");
    } catch (Exception e) {
      log.warn("Failed to delete old summary.html: {}", e.getMessage());
    }

    // Generate reports (this is I/O heavy, happens after tests are "finished")
    this.context.finishTestRun();
    generateReport();
    this.failedScenariosListener.finishReport();

    // Delete empty rerun file if all tests passed
    if (getFailedFeaturesFromListener().isEmpty()) {
      String rerunPath = System.getProperty("user.dir") + "/target/rerun/" + options.rerunFileName();
      try {
        Files.deleteIfExists(Paths.get(rerunPath));
        log.debug("Deleted empty rerun file");
      } catch (Exception e) {
        log.warn("Failed to delete empty rerun file: {}", e.getMessage());
      }
    }

    // Shutdown deferred rerun manager executor
    log.trace("Shutting down deferred rerun manager");
    try {
      deferredRerunManager.shutdown();
    } catch (Exception e) {
      log.warn("Error shutting down deferred rerun manager: {}", e.getMessage());
    }

    TestaraExtensionContext extensionContext =
        new TestaraExtensionContext(new TestaraCucumberEngineDescriptor(UniqueId.forEngine("testara-cucumber"),
            options,
            null), options);
    for (TestaraExtension extension : extensions) {
      try {
        log.trace("Running afterAll for {}", extension.getClass().getSimpleName());
        extension.afterAll(extensionContext);
      } catch (Throwable t) {
        log.warn("afterAll failed for {}: {}", extension.getClass().getSimpleName(), t.getMessage());
      }
    }

    log.trace("Post-test cleanup completed");
  }

  /**
   * Get failed features from the FailedScenariosListener for deferred rerun
   */
  private List<FeatureWithLines> getFailedFeaturesFromListener() {
    if (this.failedScenariosListener != null) {
      // Use FailedScenariosListener which already tracks failed scenarios
      return this.failedScenariosListener.getFailedFeatures();
    } else {
      // Fallback to empty list if listener not available
      log.warn("FailedScenariosListener not available, no failed scenarios to rerun");
      return new ArrayList<>();
    }
  }

  /**
   * Print a summary of the deferred rerun result
   */
  private void printRerunSummary(DeferredRerunManager.RerunResult result) {
    if (result == null) {
      log.warn("No rerun result available to print summary");
      return;
    }

    log.info("╔══════════════════════════════════════════════════════════════════════════════════════╗");
    log.info("║                        DEFERRED RERUN SUMMARY                                        ║");
    log.info("╠══════════════════════════════════════════════════════════════════════════════════════╣");
    log.info("║  Initial failed scenarios:  {} ", result.getTotalScenarios());
    log.info("║  Rerun passed:           {} ", result.getPassedScenarios());
    log.info("║  Still failing:           {} ", result.getFailedScenarios());
    log.info("║  Rerun success rate:     {} ",
        result.getTotalScenarios() > 0 ?
            String.format("%.2f%%", (result.getPassedScenarios() * 100.0 / result.getTotalScenarios())) :
            "N/A");
    log.info("╠══════════════════════════════════════════════════════════════════════════════════════╣");
  }
}

