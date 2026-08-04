package io.github.ygrip.testara.ui.hooks;

import io.cucumber.java.After;
import io.github.ygrip.testara.ui.context.StepContext;

/**
 * Runs after {@link ScreenshotHooks} (default order) has read the current step name for
 * screenshot labeling, so StepContext's ThreadLocals don't leak/stale into the next scenario
 * on pooled threads.
 */
public class StepContextHooks {

  @After(order = 50000)
  public void clearStepContext() {
    StepContext.clear();
  }

}
