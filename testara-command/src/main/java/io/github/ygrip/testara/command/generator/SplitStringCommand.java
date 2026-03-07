package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>SplitStringCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "split", overwrite = true, cacheable = true)
public class SplitStringCommand implements CommandLogic<List<String>> {
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
  public List<String> execute(List<Object> parameters) {
    return ObjectUtils.isEmpty(parameters) ?
        null :
        parameters.size() == 1 ?
            Collections.singletonList(String.valueOf(parameters.get(0))) :
            Arrays.asList(String.valueOf(parameters.get(0)).split(String.valueOf(parameters.get(1))));
  }
}
