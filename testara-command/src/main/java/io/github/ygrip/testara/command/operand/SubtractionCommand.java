package io.github.ygrip.testara.command.operand;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.NumberHelper;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>SubtractionCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "subtract", alias = "-", overwrite = true, cacheable = true)
public class SubtractionCommand implements CommandLogic<Number> {
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
  public Number execute(List<Object> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return 0;
    }
    if (parameters.size() == 1) {
      return NumberHelper.parseNumber(String.valueOf(parameters.get(0)));
    } else {
      BigDecimal a = NumberHelper.parseNumber(String.valueOf(parameters.get(0)), BigDecimal.class);
      BigDecimal b = NumberHelper.parseNumber(String.valueOf(parameters.get(1)), BigDecimal.class);
      return NumberHelper.autoBoxingNumber(safeNull(a).subtract(safeNull(b)));
    }
  }

  private BigDecimal safeNull(BigDecimal input) {
    return input == null ? new BigDecimal(0) : input;
  }
}
