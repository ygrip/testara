package io.github.ygrip.testara.ui.command;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.observation.TheElement;

/**
 * <p>OneElementCommand class.</p>
 *
 * @author yunaz.ramadhan on 12/25/2019
 * @version $Id: $Id
 */
@CommandTag(command = "element", alias = "findone", overwrite = true, cacheable = true)
public class OneElementCommand implements CommandLogic<Object> {
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object execute(List<Object> parameters) throws Exception {
    if (isBlank(parameters)) {
      return null;
    }
    return ActorManager.currentActor()
      .observe(TheElement.of(Optional.ofNullable(parameters.getFirst())
        .filter(ObjectUtils::isNotEmpty)
        .map(Object::toString)
        .orElse(null)));
  }
}
