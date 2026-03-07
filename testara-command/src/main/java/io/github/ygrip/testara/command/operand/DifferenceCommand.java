package io.github.ygrip.testara.command.operand;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.SetUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * <p>DifferenceCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "difference", overwrite = true, cacheable = true)
public class DifferenceCommand implements CommandLogic<List<Object>> {
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
  public List<Object> execute(List<Object> parameters) throws Exception {
    List<List<Object>> params = new ArrayList<>();
    for (Object obj : parameters) {
      if (CommonHelper.isCollection(obj)) {
        params.add(new ArrayList<>((Collection<?>) obj));
      } else {
        params.add(Collections.singletonList(obj));
      }
    }
    return SetUtils.difference(params);
  }
}
