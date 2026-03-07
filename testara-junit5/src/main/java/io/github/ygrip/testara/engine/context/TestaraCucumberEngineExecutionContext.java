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
import java.util.List;
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
  // Deferred rerun components
  private final DeferredRerunManager deferredRerunManager;
  @Getter
  private final List<TestaraExtension> extensions = new ArrayList<>();
  private CucumberExecutionContext context;
  private FailedScenariosListener failedScenariosListener;

  public TestaraCucumberEngineExecutionContext(ExecutionRequest request) {
    this.reportFiles = new ArrayList<>();
    this.listener = request.getEngineExecutionListener();
    this.options = new TestaraCucumberEngineOptions(request.getConfigurationParameters());

    // Initialize rerun components
    loadExtensions(options);
    this.deferredRerunManager = new DeferredRerunManager(options);
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

        TestaraTestCaseResultObserver observer =
            TestaraTestCaseResultObserver.observe(runner.getBus(), descriptor, listener, options.stepNotifications());
        try {
          log.debug("Executing test case {}", pickle.getName());
          runner.runPickle(pickle);
          log.debug("Finished test case {}", pickle.getName());
          observer.assertTestCasePassed();

          // Trace scenario finish (passed)
          ParallelExecutionTracer.scenarioFinished(pickle.getName(), true);

        } catch (Throwable exception) {
          // Check if this is a shutdown/interruption exception
          if (isShutdownException(exception)) {
            log.debug("Test case cancelled due to shutdown: {}", pickle.getName());
            try {
              observer.close();
            } catch (Exception ignored) {
            }
            ParallelExecutionTracer.scenarioFinished(pickle.getName(), false);
            return;
          }

          try {
            observer.close();
          } catch (Exception ignored) {
            // Ignore observer close errors
          }

          Status status = observer.getResult().getStatus();

          // Handle rerun based on strategy
          RerunStrategy rerunStrategy = options.getRerunStrategy();

          if (options.isRerunEnabled() && status.is(Status.FAILED)) {
            if (rerunStrategy == RerunStrategy.IMMEDIATE) {
              // Immediate rerun - existing behavior
              try {
                status = rerunFailedTestCase(1, status, pickle, descriptor);
              } catch (Throwable error) {
                if (!isShutdownException(error)) {
                  exception.addSuppressed(error);
                }
              }
            } else if (rerunStrategy == RerunStrategy.DEFERRED) {
              // Deferred rerun - just record the failure, don't retry immediately
              log.debug("Scenario {} failed, recorded for deferred rerun", pickle.getName());
            } else if (rerunStrategy == RerunStrategy.COMBINE) {
              // Combined rerun - try immediate first, then defer any remaining failures
              try {
                status = rerunFailedTestCase(1, status, pickle, descriptor);
                if (status.is(Status.FAILED)) {
                  // Still failed after immediate rerun, record for deferred rerun
                  log.debug("Scenario {} failed after immediate rerun, recorded for deferred rerun", pickle.getName());
                }
              } catch (Throwable error) {
                if (!isShutdownException(error)) {
                  exception.addSuppressed(error);
                }
                // Failed during immediate rerun, record for deferred rerun
                log.debug("Scenario {} failed during immediate rerun, recorded for deferred rerun", pickle.getName());
              }
            }
            // For RerunStrategy.NONE, do nothing - scenario fails normally
          }

          if (!status.isOk() && !getOptions().stepNotifications()) {
            // Trace scenario finish (failed)
            ParallelExecutionTracer.scenarioFinished(pickle.getName(), false);
            ExceptionHandler.trimStackTrace(exception);
            throw exception;
          }
        }
        observer.close();
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
        TestaraTestCaseResultObserver observer =
            TestaraTestCaseResultObserver.observe(runner.getBus(), descriptor, listener, options.stepNotifications());
        if (attempt == options.maxRetryFailedScenarios()) {
          observer.isRerun(true);
        }
        try {
          log.debug("Rerun #{} for test case {}", attempt, pickle.getName());
          runner.runPickle(pickle);
          log.debug("Finished rerun #{} test case {}", attempt, pickle.getName());
          currentStatus.set(observer.getResult().getStatus());
          throwable.set(observer.getResult().getError());

        } catch (Throwable error) {
          try {
            observer.close();
          } catch (Exception ignored) {
            // Ignore observer close errors
          }
          currentStatus.set(Status.FAILED);
          throwable.set(ExceptionHandler.trimStackTrace(error));
        }
        observer.close();
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
                .withReportTemplate("testara-simple-report")
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

    // Execute deferred rerun if needed (this can be slow, so do it first)
    if (deferredRerunManager.shouldExecuteDeferred()) {
      List<FeatureWithLines> failedFeatures = getFailedFeaturesFromListener();

      if (!failedFeatures.isEmpty()) {
        log.debug("Starting deferred rerun phase for {} failed features", failedFeatures.size());

        // Execute deferred rerun and wait for completion
        // This is GUARANTEED to return a result (never throws exceptions)
        DeferredRerunManager.RerunResult rerunResult;
        try {
          rerunResult = deferredRerunManager.executeDeferred(failedFeatures, this).get();
        } catch (Exception e) {
          // Safety fallback - should never happen as executeDeferred catches all exceptions
          log.error("Unexpected error getting deferred rerun result: {}",
              e.getMessage(),
              ExceptionHandler.trimStackTrace(e));
          int totalScenarios =
              getFailedFeaturesFromListener().stream().map(fail -> fail.lines().size()).reduce(0, Integer::sum);
          rerunResult =
              new DeferredRerunManager.RerunResult(totalScenarios, 0, "Deferred rerun interrupted: " + e.getMessage());
        }

        // Always print rerun summary
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

