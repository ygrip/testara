package io.github.ygrip.testara.cucumber.hooks;

import io.cucumber.java.After;
import io.github.ygrip.testara.command.CommandExecutor;

public class CommandCacheHooks {

  @After(order = 0)
  public void clearCommandExecutionCache() {
    CommandExecutor.clearExecutionCache();
  }

}
