package io.github.ygrip.testara.command.operand;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;


/**
 * <p>SizeOfCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "sizeof", alias = "size", overwrite = true, cacheable = true)
public class SizeOfCommand implements CommandLogic<Integer> {
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
    } else if (parameters.get(0) instanceof Collection) {
      return ((Collection<?>) parameters.get(0)).size();
    } else if (parameters.get(0) instanceof HashMap) {
      return ((HashMap<?, ?>) parameters.get(0)).size();
    } else if (parameters.get(0).getClass().isArray()) {
      return Math.toIntExact(Arrays.stream((Object[]) parameters.get(0)).count());
    } else if (ObjectUtils.isNotEmpty(parameters.get(0))) {
      return 1;
    } else {
      return 0;
    }
  }
}
