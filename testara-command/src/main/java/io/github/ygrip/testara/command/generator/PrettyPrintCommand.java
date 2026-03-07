package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.StringHelper;

import java.util.List;

/**
 * @author yunaz.ramadhan on 2/23/2023
 */
@CommandTag(command = "prettyprint", overwrite = true, cacheable = true)
public class PrettyPrintCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    if (CommonHelper.isBlank(parameters)) {
      return null;
    } else {
      return StringHelper.prettyPrint(parameters.get(0));
    }
  }
}
