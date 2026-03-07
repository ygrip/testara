package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.CommonHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Optional;

/**
 * <p>AnyOfCommand class.</p>
 *
 * @author yunaz.ramadhan on 06/01/2026
 * @version $Id: $Id
 */
@CommandTag(command = "anyof", alias = {"any_of"}, overwrite = true, cacheable = true)
public class AnyOfCommand implements CommandLogic<Object> {
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
  public Object execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters)) {
      return null;
    } else {
      Optional<Object> optional = parameters.stream().filter(obj -> !CommonHelper.isBlank(obj)).findFirst();
      return optional.orElse(null);
    }
  }
}
