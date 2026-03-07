package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;

import java.util.List;

/**
 * <p>NullValueCommand class.</p>
 *
 * @author yunaz.ramadhan on 11/5/2021
 * @version $Id: $Id
 */
@CommandTag(command = "nullvalue", alias = {"null value", "null"}, overwrite = true, cacheable = true)
public class NullValueCommand implements CommandLogic<Object> {
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object execute(List<Object> parameters) throws Exception {
    return null;
  }
}
