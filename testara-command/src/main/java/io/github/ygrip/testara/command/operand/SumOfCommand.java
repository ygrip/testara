package io.github.ygrip.testara.command.operand;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.NumberHelper;

import java.math.BigDecimal;
import java.util.List;


/**
 * <p>SumOfCommand class.</p>
 *
 * @author yunaz.ramadhan on 3/29/2020
 * @version $Id: $Id
 */
@CommandTag(command = "sumof", overwrite = true, cacheable = true)
public class SumOfCommand implements CommandLogic<Number> {
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
      if (CommonHelper.isCollection(parameters.get(0))) {
        List<Object> parsed = MapperHelper.toObject(parameters.get(0), new TypeReference<>() {
        });
        BigDecimal result = new BigDecimal(0);
        if (parsed == null) {
          return NumberHelper.autoBoxingNumber(result);
        }
        for (Object parameter : parsed) {
          BigDecimal number = NumberHelper.parseNumber(String.valueOf(parameter), BigDecimal.class);
          result = result.add(safeNull(number));
        }
        return NumberHelper.autoBoxingNumber(result);
      } else {
        return NumberHelper.parseNumber(String.valueOf(parameters.get(0)));
      }
    } else {
      BigDecimal result = new BigDecimal(0);
      for (Object parameter : parameters) {
        BigDecimal number = NumberHelper.parseNumber(String.valueOf(parameter), BigDecimal.class);
        result = result.add(safeNull(number));
      }
      return NumberHelper.autoBoxingNumber(result);
    }
  }

  private BigDecimal safeNull(BigDecimal input) {
    return input == null ? new BigDecimal(0) : input;
  }
}
