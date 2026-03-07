package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.StringHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>ToUpperCaseCommand class.</p>
 *
 * @author yunaz.ramadhan on 1/16/2020
 * @version $Id: $Id
 */
@CommandTag(command = "capitalize", overwrite = true, cacheable = true)
public class CapitalizeCommand implements CommandLogic<String> {
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
    return ObjectUtils.isEmpty(parameters) ? null : StringHelper.capitalize(parameters.get(0).toString());
  }
}
