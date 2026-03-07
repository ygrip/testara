package io.github.ygrip.testara.engine.listener;

import io.github.ygrip.testara.engine.descriptor.TestaraNodeDescriptor;
import io.github.ygrip.testara.engine.support.TestDescriptorOrderUtils;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.TestDescriptor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Traces parallel execution to show which scenarios run together in batches.
 * Helps identify resource conflicts and race conditions.
 */
@Log4j2
public class ParallelExecutionTracer {

  private static final Map<Long, ScenarioExecutionInfo> executionMap = new ConcurrentHashMap<>();
  private static final Map<String, List<Long>> batchGroups = new ConcurrentHashMap<>();
  private static final AtomicInteger batchCounter = new AtomicInteger(0);
  private static final ThreadLocal<Integer> threadBatch = new ThreadLocal<>();
  private static volatile boolean enabled = false;

  public static void enable() {
    enabled = true;
  }

  public static void disable() {
    enabled = false;
  }

  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * Record when a scenario starts
   */
  public static void scenarioStarted(String scenarioName, TestDescriptor descriptor) {
    if (!enabled) return;

    long threadId = Thread.currentThread().getId();
    String threadName = Thread.currentThread().getName();
    long startTime = System.currentTimeMillis();

    // Assign batch if not already assigned for this thread
    if (threadBatch.get() == null) {
      threadBatch.set(batchCounter.incrementAndGet());
    }
    int batch = threadBatch.get();

    ScenarioExecutionInfo info = new ScenarioExecutionInfo(
        scenarioName,
        threadId,
        threadName,
        batch,
        startTime,
        getExclusiveResources(descriptor),
        getOrderTag(descriptor)
    );

    executionMap.put(threadId, info);

    // Group by batch
    batchGroups.computeIfAbsent("batch-" + batch, k -> new ArrayList<>()).add(threadId);

    log.info("\n┌─────────────────────────────────────────────────────────────────────────────┐\n" +
             "│ ▶ SCENARIO STARTED                                                          │\n" +
             "├─────────────────────────────────────────────────────────────────────────────┤\n" +
             "│ Scenario:  " + String.format("%-62s", scenarioName) + "│\n" +
             "│ Batch:     " + String.format("%-62s", "Batch #" + batch) + "│\n" +
             "│ Order:     " + String.format("%-62s", "@Order=" + info.orderTag) + "│\n" +
             "│ Resources: " + String.format("%-62s", info.exclusiveResources) + "│\n" +
             "└─────────────────────────────────────────────────────────────────────────────┘");
  }

  /**
   * Record when a scenario finishes
   */
  public static void scenarioFinished(String scenarioName, boolean passed) {
    if (!enabled) return;

    long threadId = Thread.currentThread().getId();
    ScenarioExecutionInfo info = executionMap.get(threadId);

    if (info != null) {
      long endTime = System.currentTimeMillis();
      long duration = endTime - info.startTime;
      info.duration = duration;
      info.passed = passed;

      String status = passed ? "✓ PASSED" : "✗ FAILED";
      String statusColor = passed ? "" : "[FAILED] ";

      log.info("\n┌─────────────────────────────────────────────────────────────────────────────┐\n" +
               "│ ● SCENARIO FINISHED                                                         │\n" +
               "├─────────────────────────────────────────────────────────────────────────────┤\n" +
               "│ Scenario:  " + String.format("%-62s", scenarioName) + "│\n" +
               "│ Status:    " + String.format("%-62s", statusColor + status) + "│\n" +
               "│ Duration:  " + String.format("%-62s", duration + "ms") + "│\n" +
               "└─────────────────────────────────────────────────────────────────────────────┘");
    }
  }

  /**
   * Print summary of parallel execution showing which scenarios ran together
   */
  public static void printExecutionSummary() {
    if (!enabled || executionMap.isEmpty()) return;

    // Group scenarios by batch
    Map<Integer, List<ScenarioExecutionInfo>> batchMap = new HashMap<>();
    for (ScenarioExecutionInfo info : executionMap.values()) {
      batchMap.computeIfAbsent(info.batch, k -> new ArrayList<>()).add(info);
    }

    // Sort batches
    List<Integer> sortedBatches = new ArrayList<>(batchMap.keySet());
    Collections.sort(sortedBatches);

    // Calculate overall statistics
    int totalScenarios = executionMap.size();
    int totalPassed = (int) executionMap.values().stream().filter(s -> s.passed).count();
    int totalFailed = totalScenarios - totalPassed;
    int totalBatches = batchMap.size();

    // Build complete summary in one StringBuilder
    StringBuilder summary = new StringBuilder();
    summary.append("\n╔═══════════════════════════════════════════════════════════════════════════════╗\n");
    summary.append("║                    PARALLEL EXECUTION SUMMARY                                 ║\n");
    summary.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");
    summary.append(String.format("║ Total: %d scenarios in %d batches | ✓ %d passed | ✗ %d failed%s║\n",
        totalScenarios, totalBatches, totalPassed, totalFailed, 
        " ".repeat(Math.max(0, 21 - String.valueOf(totalScenarios).length() - String.valueOf(totalBatches).length()))));
    summary.append("╚═══════════════════════════════════════════════════════════════════════════════╝\n");

    // Add batch details
    for (Integer batch : sortedBatches) {
      List<ScenarioExecutionInfo> scenarios = batchMap.get(batch);
      appendBatch(summary, batch, scenarios);
    }

    // Add conflicts analysis
    appendPotentialConflicts(summary, batchMap);

    summary.append("\n╔═══════════════════════════════════════════════════════════════════════════════╗\n");
    summary.append("║                         END OF EXECUTION SUMMARY                              ║\n");
    summary.append("╚═══════════════════════════════════════════════════════════════════════════════╝");

    // Print entire summary as ONE log entry
    log.info(summary.toString());
  }

  private static void appendBatch(StringBuilder sb, int batch, List<ScenarioExecutionInfo> scenarios) {
    long minStart = scenarios.stream().mapToLong(s -> s.startTime).min().orElse(0);
    long maxEnd = scenarios.stream().mapToLong(s -> s.startTime + s.duration).max().orElse(0);
    long totalDuration = maxEnd - minStart;

    int passed = (int) scenarios.stream().filter(s -> s.passed).count();
    int failed = scenarios.size() - passed;

    sb.append("\n┌─────────────────────────────────────────────────────────────────────────────┐\n");
    sb.append(String.format("│ BATCH #%-2d - %d scenarios (%d passed, %d failed) - Duration: %dms%s│\n",
        batch, scenarios.size(), passed, failed, totalDuration,
        " ".repeat(Math.max(0, 30 - String.valueOf(totalDuration).length()))));
    sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");

    for (ScenarioExecutionInfo info : scenarios) {
      String status = info.passed ? "✓" : "✗";
      String statusLabel = info.passed ? "PASS" : "FAIL";

      sb.append(String.format("│ %s [%s] %-55s (%5dms)%n",
          status,
          statusLabel,
          truncate(info.scenarioName, 55),
          info.duration));

      if (!info.exclusiveResources.isEmpty()) {
        sb.append(String.format("│      Resources: %s%n", info.exclusiveResources));
      }
      if (!info.orderTag.equals("none")) {
        sb.append(String.format("│      Order: @Order=%s%n", info.orderTag));
      }
    }

    sb.append("└─────────────────────────────────────────────────────────────────────────────┘");
  }

  private static void appendPotentialConflicts(StringBuilder sb, Map<Integer, List<ScenarioExecutionInfo>> batchMap) {
    sb.append("\n┌─────────────────────────────────────────────────────────────────────────────┐\n");
    sb.append("│ POTENTIAL CONFLICTS ANALYSIS                                                │\n");
    sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");

    boolean foundConflicts = false;

    for (Map.Entry<Integer, List<ScenarioExecutionInfo>> entry : batchMap.entrySet()) {
      List<ScenarioExecutionInfo> scenarios = entry.getValue();

      // Check for failures in same batch
      List<ScenarioExecutionInfo> failures = scenarios.stream()
          .filter(s -> !s.passed)
          .collect(java.util.stream.Collectors.toList());

      if (!failures.isEmpty()) {
        foundConflicts = true;
        sb.append(String.format("│ ⚠ Batch #%d had %d failures running in parallel:%n", 
            entry.getKey(), failures.size()));

        for (ScenarioExecutionInfo failed : failures) {
          sb.append(String.format("│   ✗ %s%n", truncate(failed.scenarioName, 70)));

          // Check if any other scenario in same batch could have conflicted
          for (ScenarioExecutionInfo other : scenarios) {
            if (other != failed && couldConflict(failed, other)) {
              sb.append(String.format("│     ⚠ Possible conflict with: %s%n", 
                  truncate(other.scenarioName, 55)));
            }
          }
        }
        sb.append("│\n");
      }
    }

    if (!foundConflicts) {
      sb.append("│ ✓ No obvious conflicts detected                                             │\n");
    } else {
      sb.append("│                                                                             │\n");
      sb.append("│ RECOMMENDATION:                                                             │\n");
      sb.append("│ • Enable resource detection: cucumber.execution.resource-detection.enabled  │\n");
      sb.append("│ • Add @ExclusiveResource tags to conflicting scenarios                      │\n");
      sb.append("│ • Add @Order tags to enforce execution sequence                             │\n");
    }

    sb.append("└─────────────────────────────────────────────────────────────────────────────┘");
  }

  private static boolean couldConflict(ScenarioExecutionInfo s1, ScenarioExecutionInfo s2) {
    // Check if scenarios overlap in time
    long s1Start = s1.startTime;
    long s1End = s1.startTime + s1.duration;
    long s2Start = s2.startTime;
    long s2End = s2.startTime + s2.duration;

    boolean timeOverlap = (s1Start <= s2End) && (s2Start <= s1End);

    // Check if they share resources
    boolean resourceConflict = !Collections.disjoint(s1.exclusiveResources, s2.exclusiveResources);

    return timeOverlap && (resourceConflict || s1.exclusiveResources.isEmpty());
  }

  private static String truncate(String str, int maxLength) {
    if (str.length() <= maxLength) {
      return str;
    }
    return str.substring(0, maxLength - 3) + "...";
  }

  private static Set<String> getExclusiveResources(TestDescriptor descriptor) {
    Set<String> resources = new HashSet<>();
    if (descriptor instanceof TestaraNodeDescriptor) {
      ((TestaraNodeDescriptor) descriptor).getExclusiveResources()
          .forEach(res -> resources.add(res.getKey()));
    }
    return resources;
  }

  private static String getOrderTag(TestDescriptor descriptor) {
    Integer order = TestDescriptorOrderUtils.getOrder(descriptor);
    return order == Integer.MAX_VALUE ? "none" : String.valueOf(order);
  }

  public static void reset() {
    executionMap.clear();
    batchGroups.clear();
    batchCounter.set(0);
    threadBatch.remove();
  }

  static class ScenarioExecutionInfo {
    final String scenarioName;
    final long threadId;
    final String threadName;
    final int batch;
    final long startTime;
    final Set<String> exclusiveResources;
    final String orderTag;
    long duration;
    boolean passed = true;

    ScenarioExecutionInfo(String scenarioName, long threadId, String threadName,
        int batch, long startTime, Set<String> exclusiveResources, String orderTag) {
      this.scenarioName = scenarioName;
      this.threadId = threadId;
      this.threadName = threadName;
      this.batch = batch;
      this.startTime = startTime;
      this.exclusiveResources = exclusiveResources;
      this.orderTag = orderTag;
    }
  }
}