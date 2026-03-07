package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>ParseBooleanCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "boolean", overwrite = true, cacheable = true)
public class ParseBooleanCommand implements CommandLogic<Boolean> {
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
  public Boolean execute(List<Object> parameters) {
    return ObjectUtils.isNotEmpty(parameters) && (ObjectUtils.isNotEmpty(parameters.get(0)) && Boolean.parseBoolean(
        parameters.get(0).toString()));
  }
}
