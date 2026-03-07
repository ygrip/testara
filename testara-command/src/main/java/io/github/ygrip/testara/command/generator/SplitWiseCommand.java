package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.NumberHelper;

import java.util.List;

@CommandTag(command = "splitwise", alias = {"split wise", "split_wise"}, overwrite = true, cacheable = true)
public class SplitWiseCommand implements CommandLogic<List<Number>> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public List<Number> execute(List<Object> parameters) throws Exception {
    if (parameters == null || parameters.isEmpty()) {
      return null;
    } else {
      return parameters.size() < 2 ?
          NumberHelper.splitWise((Number) parameters.get(0), 1) :
          NumberHelper.splitWise((Number) parameters.get(0), Integer.parseInt(parameters.get(1).toString()));
    }
  }
}
