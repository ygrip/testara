package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>SubStringCommand class.</p>
 *
 * @author yunaz.ramadhan on 11/5/2021
 * @version $Id: $Id
 */
@CommandTag(command = "substring", alias = "sub string", overwrite = true, cacheable = true)
public class SubStringCommand implements CommandLogic<String> {
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
    if (ObjectUtils.isEmpty(parameters)) {
      return "";
    } else {
      if (parameters.size() == 1) {
        return String.valueOf(parameters.get(0));
      } else if (parameters.size() == 2) {
        return String.valueOf(parameters.get(0)).substring(Integer.parseInt(String.valueOf(parameters.get(1))));
      } else {
        return String.valueOf(parameters.get(0))
            .substring(Integer.parseInt(String.valueOf(parameters.get(1))),
                Integer.parseInt(String.valueOf(parameters.get(2))));
      }
    }
  }
}
