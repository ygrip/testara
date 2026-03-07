package io.github.ygrip.testara.spring.scope;

import io.github.ygrip.testara.core.registry.ScopeContext;
import lombok.extern.log4j.Log4j2;

/**
 * Spring-aware ScopeContext implementation.
 * <p>
 * Provides scope key resolution that integrates with Spring's
 * AutomationScope for thread-scoped beans.
 * <p>
 * This is registered via SPI to replace the default scope context
 * when Spring is present.
 */
@Log4j2
public final class SpringScopeContext implements ScopeContext {

  private static final String SCOPE_PREFIX = "testara-spring-test-";
  
  // Thread-local for test-specific scope identification
  private static final ThreadLocal<String> TEST_SCOPE_ID = new InheritableThreadLocal<>();

  /**
   * Set the current test scope ID.
   * Call this at the beginning of each test.
   */
  public static void setTestScopeId(String scopeId) {
    TEST_SCOPE_ID.set(scopeId);
    log.trace("Set test scope ID: {}", scopeId);
  }

  /**
   * Get the current test scope ID.
   */
  public static String getTestScopeId() {
    return TEST_SCOPE_ID.get();
  }

  /**
   * Clear the current test scope ID.
   * Call this at the end of each test.
   */
  public static void clearTestScopeId() {
    TEST_SCOPE_ID.remove();
    log.trace("Cleared test scope ID");
  }

  @Override
  public String currentScopeKey() {
    // If a test scope ID is set, use it
    String testScopeId = TEST_SCOPE_ID.get();
    if (testScopeId != null) {
      return testScopeId;
    }
    
    // Otherwise, use thread identity for thread-scoped beans
    // This aligns with AutomationScope's getConversationId()
    return SCOPE_PREFIX + System.identityHashCode(Thread.currentThread());
  }
}
