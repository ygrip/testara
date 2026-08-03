package io.github.ygrip.testara.core.context;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.JUnit5ScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

@Tag("context")
@Execution(ExecutionMode.CONCURRENT)
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ContextTests extends BaseTests {
  // NOTE: TestFramework now holds a single run-level TestContext (see TestFramework javadoc) -
  // it is no longer a per-thread ThreadLocal, so concurrently running test methods can no longer
  // each install (and observe) their own distinct TestContext via TestFramework.initialize().
  // This test therefore keeps its own per-invocation TestContext in a thread-local and drives
  // isolation directly through it (via distinct JUnit5ScopeContext keys), instead of round
  // tripping through TestFramework - which still exercises the exact same DefaultTestContext /
  // RootRegistry scope-isolation behavior this test is meant to verify.
  private static final ThreadLocal<TestContext> CURRENT_TEST_CONTEXT = new ThreadLocal<>();
  private static final Set<TestContext> contexts = ConcurrentHashMap.newKeySet();
  // Store DataHolder instances captured during test execution (when scope is active)
  private static final Map<String, DataHolder> dataHoldersByContext = new ConcurrentHashMap<>();

  @AfterAll
  static void verify() {
    // Verify that multiple contexts were created
    assertThat(contexts.size(), greaterThan(1));

    // Verify that DataHolder instances captured during test execution are different
    // This ensures proper isolation when dynamic scope is active
    assertThat(dataHoldersByContext.size(), greaterThan(1));

    DataHolder firstDataHolder = dataHoldersByContext.values().stream().toList().getFirst();
    DataHolder lastDataHolder = dataHoldersByContext.values().stream().toList().getLast();

    assertThat(firstDataHolder, is(not(equalTo(lastDataHolder))));
  }

  @BeforeEach
  public void beforeEach() {
    TestContext testContext = new DefaultTestContext(TestFramework.context().configuration());
    CURRENT_TEST_CONTEXT.set(testContext);
    JUnit5ScopeContext.enter(String.format("%s#%s", getClass().getName(), System.identityHashCode(testContext)));
  }

  @AfterEach
  public void afterEach() {
    // Clean up scope context to ensure proper isolation
    JUnit5ScopeContext.exit();
    CURRENT_TEST_CONTEXT.remove();
  }

  @Test
  public void onSameThread() {
    TestContext context = CURRENT_TEST_CONTEXT.get();
    DataHolder dataHolder1 = context.get(DataHolder.class);
    DataHolder dataHolder2 = context.get(DataHolder.class);

    assertThat(dataHolder1, equalTo(dataHolder2));
  }

  @RepeatedTest(2)
  public void onDifferentThread() throws InterruptedException {
    TestContext context = CURRENT_TEST_CONTEXT.get();
    contexts.add(context);

    // Capture DataHolder during test execution when dynamic scope is active
    // This ensures we verify isolation at the right time
    DataHolder dataHolder = context.get(DataHolder.class);
    String contextId = ((DefaultTestContext) context).scopeId();
    dataHoldersByContext.put(contextId, dataHolder);
  }
}
