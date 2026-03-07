package io.github.ygrip.testara.engine.extension;

import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;

/**
 * Extension that manages logging context (MDC) for parallel test execution.
 * <p>
 * For virtual threads, Log4j2's ThreadContext may not work reliably because
 * virtual threads can run on different carrier threads. This extension uses
 * multiple strategies:
 * <ol>
 *   <li>Sets MDC values via ThreadContext (works when properly configured)</li>
 *   <li>Logs clear START/END markers with scenario info embedded in message</li>
 *   <li>Includes thread name which contains scenario-specific prefix</li>
 * </ol>
 * <p>
 * MDC keys available (when ThreadContext works):
 * <ul>
 *   <li><b>scenario</b>: Short scenario name for easy identification</li>
 *   <li><b>scenarioId</b>: 8-char unique ID for filtering</li>
 *   <li><b>feature</b>: Feature file name</li>
 * </ul>
 * <p>
 * For virtual threads, add to JVM args: -Dlog4j2.isThreadContextMapInheritable=true
 * <p>
 * Example log4j2 pattern: %d{HH:mm:ss.SSS} %-5level [%X{scenario}] (%X{scenarioId}) %m%n
 */
@Log4j2
public class LoggingExtension implements TestaraExtension {

  private static final String MDC_SCENARIO = "scenario";
  private static final String MDC_SCENARIO_ID = "scenarioId";
  private static final String MDC_FEATURE = "feature";
  
  // Store context per-thread for virtual thread compatibility
  private static final ThreadLocal<ScenarioContext> SCENARIO_CONTEXT = new InheritableThreadLocal<>();

  static {
    // Configure Log4j2 for better virtual thread support
    // This should be set before Log4j2 initializes, but we try anyway
    System.setProperty("log4j2.isThreadContextMapInheritable", "true");
  }

  @Override
  public void beforeEach(TestaraExtensionContext ctx) {
    String displayName = ctx.getDisplayName();
    String uniqueId = ctx.getUniqueId();
    
    // Extract context info
    String shortName = truncate(displayName, 50);
    String shortId = generateShortId(uniqueId);
    String feature = extractFeatureName(uniqueId);
    
    // Store in our own ThreadLocal for virtual thread compatibility
    ScenarioContext scenarioContext = new ScenarioContext(shortName, shortId, feature, displayName);
    SCENARIO_CONTEXT.set(scenarioContext);
    
    // Also set MDC values (may or may not work with virtual threads)
    ThreadContext.put(MDC_SCENARIO, shortName);
    ThreadContext.put(MDC_SCENARIO_ID, shortId);
    ThreadContext.put(MDC_FEATURE, feature);
    
    // Log scenario start marker with all info embedded in message
    // This works regardless of MDC/ThreadContext issues
    log.info("""
      
      ═══════════════════════════════════════════════════════════════
      ▶ SCENARIO START [{}] [{}]
        Name   : {}
      ═══════════════════════════════════════════════════════════════
      """, shortId, feature, displayName);
  }

  @Override
  public void afterEach(TestaraExtensionContext ctx) {
    ScenarioContext scenarioContext = SCENARIO_CONTEXT.get();
    String uniqueId = ctx.getUniqueId();
    String shortId = scenarioContext != null ? scenarioContext.shortId : generateShortId(ctx.getUniqueId());
    String displayName = ctx.getDisplayName();
    String feature = extractFeatureName(uniqueId);
    String status = ctx.getExecutionException().isPresent() ? "FAILED" : "SUCCESS";
    
    // Log scenario end marker
    log.info("""
      
      ═══════════════════════════════════════════════════════════════
      ◀ SCENARIO END [{}] [{}]
        Name   : {}
        Status : {}
      ═══════════════════════════════════════════════════════════════
      """, shortId, feature, displayName, status);
    
    // Clear our ThreadLocal
    SCENARIO_CONTEXT.remove();
    
    // Clear MDC
    ThreadContext.remove(MDC_SCENARIO);
    ThreadContext.remove(MDC_SCENARIO_ID);
    ThreadContext.remove(MDC_FEATURE);
  }

  /**
   * Get current scenario context (for use by other components).
   * Returns null if not in a scenario context.
   */
  public static ScenarioContext getCurrentContext() {
    return SCENARIO_CONTEXT.get();
  }

  /**
   * Get current scenario ID for logging purposes.
   * Falls back to "no-scenario" if not available.
   */
  public static String getCurrentScenarioId() {
    ScenarioContext ctx = SCENARIO_CONTEXT.get();
    return ctx != null ? ctx.shortId : "no-scenario";
  }

  /**
   * Generate a short unique ID from the full unique ID.
   * Uses first 8 chars of hash for brevity while maintaining uniqueness.
   */
  private String generateShortId(String uniqueId) {
    if (uniqueId == null || uniqueId.isEmpty()) {
      return "unknown";
    }
    int hash = uniqueId.hashCode();
    return String.format("%08x", hash).substring(0, 8);
  }

  /**
   * Extract feature name from unique ID.
   * Handles URL-encoded paths (file%3A%2F...).
   */
  private String extractFeatureName(String uniqueId) {
    if (uniqueId == null) {
      return "unknown";
    }
    
    try {
      // URL decode if needed
      String decoded = java.net.URLDecoder.decode(uniqueId, java.nio.charset.StandardCharsets.UTF_8);
      
      // Try to extract feature name from path
      int featureStart = decoded.indexOf("[feature:");
      if (featureStart >= 0) {
        int featureEnd = decoded.indexOf("]", featureStart);
        if (featureEnd > featureStart) {
          String featurePath = decoded.substring(featureStart + 9, featureEnd);
          // Extract just the filename without path and extension
          int lastSlash = Math.max(featurePath.lastIndexOf('/'), featurePath.lastIndexOf('\\'));
          String filename = lastSlash >= 0 ? featurePath.substring(lastSlash + 1) : featurePath;
          // Remove .feature extension
          if (filename.endsWith(".feature")) {
            filename = filename.substring(0, filename.length() - 8);
          }
          return filename;
        }
      }
    } catch (Exception e) {
      // Ignore decoding errors
    }
    return "unknown";
  }

  /**
   * Truncate string to max length, adding "..." if truncated.
   */
  private String truncate(String str, int maxLength) {
    if (str == null) {
      return "unknown";
    }
    if (str.length() <= maxLength) {
      return str;
    }
    return str.substring(0, maxLength - 3) + "...";
  }

  /**
   * Holds scenario context information for the current test.
   */
  public record ScenarioContext(String shortName, String shortId, String feature, String fullName) {
  }
}
