package io.github.ygrip.testara.cucumber.hooks;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.cucumber.java.After;

public class DataCleanUpHooks {

  @After(order = 0)
  public void resetAfterTest() {
    DefaultProperties properties = TestFramework.context().configuration().get(DefaultProperties.class);

    if (properties.getResetRequestAfterEachScenario()) {
      TestFramework.context().get(DataHolder.class).resetRequestsData();
    }
    if (properties.getResetResponseAfterEachScenario()) {
      TestFramework.context().get(DataHolder.class).resetResponsesData();
    }
  }

}
