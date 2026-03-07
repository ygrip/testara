package io.github.ygrip.testara.ui.command;

import java.util.List;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.observation.ThisPage;

@CommandTag(command = "current_url", overwrite = true)
public class GetCurrentUrlCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    return ActorManager.currentActor()
      .observe(ThisPage.url());
  }
}
