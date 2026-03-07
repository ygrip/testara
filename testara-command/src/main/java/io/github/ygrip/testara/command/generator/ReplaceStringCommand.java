package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>ReplaceStringCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "replace", alias = "rep", overwrite = true, cacheable = true)
public class ReplaceStringCommand implements CommandLogic<String> {
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
  public String execute(List<Object> parameters) {
    return ObjectUtils.isEmpty(parameters) ?
        "" :
        parameters.size() == 1 ?
            String.valueOf(parameters.get(0)) :
            parameters.size() == 2 ?
                String.valueOf(parameters.get(0)).replaceAll(String.valueOf(parameters.get(1)), "") :
                String.valueOf(parameters.get(0))
                    .replaceAll(String.valueOf(parameters.get(1)), String.valueOf(parameters.get(2)));
  }
}
