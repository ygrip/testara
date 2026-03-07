package io.github.ygrip.testara.command.operand;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>ModulationCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "mod", alias = "%", overwrite = true, cacheable = true)
public class ModulationCommand implements CommandLogic<Double> {
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
    return ObjectUtils.isEmpty(parameters) ?
        0.0 :
        parameters.size() == 1 ?
            Double.parseDouble(String.valueOf(parameters.get(0))) :
            Double.parseDouble(String.valueOf(parameters.get(0)))
                % Double.parseDouble(String.valueOf(parameters.get(1)));
  }
}
