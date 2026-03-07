package io.github.ygrip.testara.engine.listener;

import io.github.ygrip.testara.core.file.FileHelper;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe listener for tracking failed test scenarios.
 * Fully supports concurrent, parallel, and virtual thread execution.
 *
 */
@Log4j2
public class FailedScenariosListener implements ConcurrentEventListener {
  // Lock-free singleton using AtomicReference - NO SYNCHRONIZED BLOCKS!
  // This prevents thread pinning when using virtual threads
  private static final AtomicReference<FailedScenariosListener> instanceRef = new AtomicReference<>();
  
  // Thread-safe map: URI -> Set of failed line numbers
  // Uses ConcurrentHashMap.newKeySet() for thread-safe Set values
  private final ConcurrentHashMap<URI, Set<Integer>> featureAndFailedLinesMapping = new ConcurrentHashMap<>();
  
  private final String filePath;
  
  // Statistics for debugging parallel execution
  private final AtomicInteger totalTestsProcessed = new AtomicInteger(0);
  private final AtomicInteger totalFailuresRecorded = new AtomicInteger(0);
  private final AtomicInteger totalSuccessesRecorded = new AtomicInteger(0);

  private FailedScenariosListener(String filePath) {
    this.filePath = filePath;
    log.debug("FailedScenariosListener created for path: {}", filePath);
  }

  /**
   * LOCK-FREE thread-safe singleton factory using AtomicReference.
   * ZERO THREAD PINNING - fully optimized for virtual threads!
   * 
   * Uses compareAndSet for atomic singleton creation without synchronized blocks.
   * This is crucial for virtual threads as synchronized blocks can cause thread pinning.
   */
  public static FailedScenariosListener reportTo(String filePath) {
    FailedScenariosListener instance = instanceRef.get();
    
    if (instance == null) {
      // Create new instance
      FailedScenariosListener newInstance = new FailedScenariosListener(filePath);
      
      // Atomically set if not already set (lock-free CAS operation)
      if (instanceRef.compareAndSet(null, newInstance)) {
        // We won the race, our instance is now the singleton
        return newInstance;
      } else {
        // Someone else won the race, use their instance
        return instanceRef.get();
      }
    }
    
    return instance;
  }

  /**
   * Handle test case finished event - thread-safe for parallel execution.
   * Can be called concurrently from multiple threads (platform or virtual).
   */
  private void handleTestCaseFinished(TestCaseFinished event) {
    totalTestsProcessed.incrementAndGet();
    
    if (!event.getResult().getStatus().isOk()) {
      this.recordTestFailed(event.getTestCase());
    } else {
      this.recordTestSuccess(event.getTestCase());
    }
  }

  /**
   * Record a test success - removes the failure if it was previously recorded.
   * Thread-safe: Uses atomic operations on ConcurrentHashMap and thread-safe Set.
   * 
   * This is important for deferred reruns where a previously failed test may pass on retry.
   */
  private void recordTestSuccess(TestCase testCase) {
    totalSuccessesRecorded.incrementAndGet();
    
    URI uri = testCase.getUri();
    Integer lineNumber = testCase.getLocation().getLine();
    
    // Atomically update the failures set for this URI
    featureAndFailedLinesMapping.computeIfPresent(uri, (key, failures) -> {
      failures.remove(lineNumber);
      
      // Return null to remove the entry if no failures remain for this feature
      // This is atomic and thread-safe with ConcurrentHashMap
      if (failures.isEmpty()) {
        return null; // Remove entry from map
      }
      
      return failures; // Keep entry with updated set
    });
  }

  /**
   * Record a test failure - adds the failure to the tracking map.
   * Thread-safe: Uses computeIfAbsent with thread-safe Set.
   */
  private void recordTestFailed(TestCase testCase) {
    totalFailuresRecorded.incrementAndGet();
    
    URI uri = testCase.getUri();
    Integer lineNumber = testCase.getLocation().getLine();
    
    // Get or create thread-safe set for this URI and add the failed line
    Set<Integer> failedLines = getFailedTestCaseLines(uri);
    failedLines.add(lineNumber);
  }

  /**
   * Get or create a thread-safe set of failed test case lines for a given URI.
   * Uses ConcurrentHashMap.newKeySet() for thread-safe Set implementation.
   */
  private Set<Integer> getFailedTestCaseLines(URI uri) {
    // ConcurrentHashMap.newKeySet() creates a thread-safe Set backed by ConcurrentHashMap
    // This is safe for concurrent add/remove operations from multiple threads
    return this.featureAndFailedLinesMapping.computeIfAbsent(uri, 
        (k) -> ConcurrentHashMap.newKeySet());
  }

  /**
   * Get a snapshot of all currently failed features.
   * Thread-safe: Creates defensive copies to avoid concurrent modification issues.
   * 
   * This method can be called while tests are still running, so it needs to be
   * thread-safe and provide a consistent snapshot at a point in time.
   */
  public List<FeatureWithLines> getFailedFeatures() {
    List<FeatureWithLines> results = new ArrayList<>();

    // Iterate over a snapshot of the map entries
    // ConcurrentHashMap's entrySet() provides weakly consistent iteration
    // (won't throw ConcurrentModificationException, but may reflect updates during iteration)
    for (Map.Entry<URI, Set<Integer>> entry : this.featureAndFailedLinesMapping.entrySet()) {
      URI uri = entry.getKey();
      Set<Integer> failedLines = entry.getValue();
      
      // Create defensive copy of the set to avoid concurrent modification
      // The Set itself is thread-safe, but we copy to ensure immutability for the caller
      Set<Integer> failedLinesCopy = Set.copyOf(failedLines);
      
      if (!failedLinesCopy.isEmpty()) {
        FeatureWithLines featureWithLines = FeatureWithLines.create(relativize(uri), failedLinesCopy);
        results.add(featureWithLines);
      }
    }

    return results;
  }

  private URI relativize(URI uri) {
    if (!"file".equals(uri.getScheme())) {
      return uri;
    } else if (!uri.isAbsolute()) {
      return uri;
    } else {
      try {
        URI root = (new File("")).toURI();
        URI relative = root.relativize(uri);
        return new URI("file", relative.getSchemeSpecificPart(), relative.getFragment());
      } catch (URISyntaxException var3) {
        throw new IllegalArgumentException(var3.getMessage(), var3);
      }
    }
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestCaseFinished.class, this::handleTestCaseFinished);
  }

  /**
   * Finish the report and write failed scenarios to file.
   * Thread-safe: Uses defensive copying to create a consistent snapshot.
   * 
   * This method should be called after all tests complete, but uses thread-safe
   * operations in case any lingering test events are still being processed.
   */
  public void finishReport() {
    if (this.featureAndFailedLinesMapping.isEmpty()) {
      log.debug("No failed scenarios to report");
      return;
    }

    try {
      // Create a defensive copy of the map for consistent snapshot
      // This prevents concurrent modification during file writing
      Map<URI, Set<Integer>> snapshot = Map.copyOf(featureAndFailedLinesMapping);
      
      StringBuilder builder = new StringBuilder();
      
      snapshot.forEach((uri, failedLines) -> {
        // Create defensive copy of the set as well
        Set<Integer> failedLinesCopy = Set.copyOf(failedLines);
        
        if (!failedLinesCopy.isEmpty()) {
          FeatureWithLines featureWithLines = FeatureWithLines.create(relativize(uri), failedLinesCopy);
          builder.append(featureWithLines);
          builder.append(System.lineSeparator());
        }
      });
      
      if (builder.length() > 0) {
        FileHelper.writeToFile(builder.toString(), filePath);
        log.info("Rerun file created at {} with {} failed features (total failures recorded: {})",
            filePath,
            snapshot.size(),
            totalFailuresRecorded.get());
      } else {
        log.debug("No failed scenarios to write (all may have passed on retry)");
      }
      
    } catch (Exception e) {
      log.warn("Failed to generate rerun file at {}: {}", filePath, e.getMessage(), e);
    }
  }
}
