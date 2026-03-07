package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.NumberHelper;

import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;

/**
 * <p>ParseFloatCommand class.</p>
 *
 * @author yunaz.ramadhan on 2/15/2020
 * @version $Id: $Id
 */
@CommandTag(command = "float", overwrite = true, cacheable = true)
public class ParseFloatCommand implements CommandLogic<Float> {
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
  public Float execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters)) {
      return null;
    }
    return NumberHelper.parseNumber(String.valueOf(parameters.getFirst()), Float.class);
  }
}
