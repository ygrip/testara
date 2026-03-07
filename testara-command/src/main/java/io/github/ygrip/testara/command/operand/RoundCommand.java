package io.github.ygrip.testara.command.operand;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>RoundCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "round", overwrite = true, cacheable = true)
public class RoundCommand implements CommandLogic<Double> {
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
  public Double execute(List<Object> parameters) {
    return ObjectUtils.isEmpty(parameters) ? 0.0 : Math.round(Double.parseDouble(String.valueOf(parameters.get(0))));
  }
}
