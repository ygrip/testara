package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.factory.ObjectFactory;

public final class TestFramework {
  private static final ThreadLocal<TestContext> CONTEXT = new InheritableThreadLocal<>();

  private TestFramework() {
  }

  public static void initialize(TestContext ctx) {
    CONTEXT.set(ctx);
  }

  public static TestContext context() {
    TestContext ctx = CONTEXT.get();
    if (ctx == null) {
      throw new IllegalStateException("TestContext not initialized");
    }
    return ctx;
  }

  public static TestConfiguration configuration() {
    return context().configuration();
  }

  public static ObjectFactory factory() {
    return context().factory();
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
