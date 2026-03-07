package io.github.ygrip.testara.ui.command;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.observation.TheCss;


@CommandTag(command = "get_css_value", overwrite = true)
public class GetCssValueCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    return isBlank(parameters) ?
      null :
      ActorManager.currentActor()
        .observe(TheCss.of(Optional.ofNullable(parameters.getFirst())
            .filter(ObjectUtils::isNotEmpty)
            .map(Object::toString)
            .orElse(null))
          .on(Optional.ofNullable(parameters.get(1))
            .filter(ObjectUtils::isNotEmpty)
            .map(Object::toString)
            .orElse(null)));
  }
}
