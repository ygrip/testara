package io.github.ygrip.testara.core.registry;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

/**
 * JUnit 5 ScopeContext that uses test unique IDs as scope keys.
 * Supports both class-level (@BeforeAll) and method-level (@BeforeEach) scopes.
 */
public final class JUnit5ScopeContext implements ScopeContext {
  private static final String DEFAULT_SCOPE = "junit5-default";
  private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_CLASS = new ThreadLocal<>();

  /**
   * Enter class-level scope (called during @BeforeAll).
   */
  public static void enterClass(ExtensionContext ctx) {
    CURRENT_CLASS.set(getClassScope(ctx));
  }

  public static String getClassScope(ExtensionContext ctx) {
    Optional<Class<?>> testClass = ctx.getTestClass();
    return testClass.map(Class::getName).orElse(DEFAULT_SCOPE);
  }

  /**
   * Enter test method scope (called during @BeforeEach).
   */
  public static void enter(ExtensionContext ctx) {
    CURRENT_TEST.set(ctx.getUniqueId());
  }

  /**
   * Enter test method scope (called during @BeforeEach).
   */
  public static void enter(String customId) {
    CURRENT_TEST.set(customId);
  }

  /**
   * Exit test method scope (called during @AfterEach).
   */
  public static void exit() {
    CURRENT_TEST.remove();
  }

  /**
   * Exit class-level scope (called during @AfterAll).
   */
  public static void exitClass() {
    CURRENT_CLASS.remove();
  }

  @Override
  public String currentScopeKey() {
    // Prefer test method scope, fall back to class scope
    String testScope = CURRENT_TEST.get();
    if (testScope != null) {
      return testScope;
    }

    String classScope = CURRENT_CLASS.get();
    if (classScope != null) {
      return classScope;
    }

    // Fallback for edge cases (should rarely happen)
    return DEFAULT_SCOPE;
  }
}
