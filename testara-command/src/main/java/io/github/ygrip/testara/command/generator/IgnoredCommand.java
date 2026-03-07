package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.command.model.CommandTag;

import java.util.List;

/**
 * <p>IgnoredCommand class.</p>
 *
 * @author yunaz.ramadhan on 1/26/2021
 * @version $Id: $Id
 */
@CommandTag(command = "ignored", alias = {"!", "ignore"}, overwrite = true, cacheable = true)
public class IgnoredCommand implements CommandLogic<String> {
  /** {@inheritDoc} */
  @Override
  public boolean preProcessParameters() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public String execute(List<Object> parameters) throws Exception {
    return CommandModel.builder().parameters(parameters).build().printParameters();
  }
}
