package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>ParseStringCommand class.</p>
 *
 * @author yunaz.ramadhan on 3/28/2020
 * @version $Id: $Id
 */
@CommandTag(command = "string", overwrite = true, cacheable = true)
public class ParseStringCommand implements CommandLogic<String> {
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
  public String execute(List<Object> parameters) throws Exception {
    return ObjectUtils.isEmpty(parameters) ? null : parameters.get(0) == null ? null : parameters.get(0).toString();
  }
}
