package io.github.ygrip.testara.core;

import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RootRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInstance;

@DisplayNameGeneration(CustomTestNameGenerator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTests {

  @BeforeAll
  void bootstrapFramework() {
    registerInfrastructure(RootRegistry.instance());
    bootstrapSlices(RootRegistry.instance());
  }

  public TestContext context() {
    return TestFramework.context();
  }

  /**
   * Hook for registering infrastructure objects
   * (ObjectMapper, ClassScanner, HTTP client, etc.)
   */
  protected void registerInfrastructure(RootRegistry registry) {
    // default: no-op
  }

  /**
   * Hook for enabling slice modules (API / UI / DB / etc.)
   */
  protected void bootstrapSlices(RootRegistry registry) {
    // default: no-op
  }

  @AfterAll
  void shutdownFramework() {
    shutdownInfrastructure();
  }

  protected void shutdownInfrastructure() {
    // Optional: shutdown executors, close resources
  }

}
