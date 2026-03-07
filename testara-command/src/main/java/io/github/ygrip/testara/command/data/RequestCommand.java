package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>RequestCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "request", overwrite = true)
public class RequestCommand implements CommandLogic<Object> {
  /** {@inheritDoc} */
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public Object execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters) || ObjectUtils.isEmpty(parameters.get(0))) {
      return null;
    }
    final String path = parameters.get(0).toString();

    try {
      return TestFramework.context().get(DataHolder.class).getRequest(path).getValue();
    } catch (Exception ignored) {
      return null;
    }
  }
}
