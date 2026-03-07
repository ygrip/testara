package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>CountCharCommand class.</p>
 *
 * @author yunaz.ramadhan on 11/5/2021
 * @version $Id: $Id
 */
@CommandTag(command = "countchar", alias = {"count char"}, overwrite = true, cacheable = true)
public class CountCharCommand implements CommandLogic<Integer> {
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
  public Integer execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters)) {
      return 0;
    } else {
      return String.valueOf(parameters.get(0)).length();
    }
  }
}
